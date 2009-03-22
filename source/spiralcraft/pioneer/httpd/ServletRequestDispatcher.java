//
// Copyright (c) 1998,2007 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//
package spiralcraft.pioneer.httpd;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletRequest;
// import javax.servlet.ServletRequestWrapper;
import javax.servlet.ServletResponse;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import spiralcraft.log.ClassLog;
import spiralcraft.pioneer.httpd.AbstractHttpServletRequest.RequestSource;

import java.io.IOException;

public class ServletRequestDispatcher
  implements RequestDispatcher
{
 
 
  private final static ClassLog log
    =ClassLog.getInstance(ServletRequestDispatcher.class);
  
  private final HttpServiceContext context;
  private final String servletName;
  
  public ServletRequestDispatcher(HttpServiceContext context,String servletName)
  { 
    this.context=context;
    this.servletName=servletName;
  }
  
  public void include(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    if (context.isDebug())
    { 
      log.fine
        ("Including servlet "+servletName+" in "
        +((HttpServletRequest) request).getRequestURL()
        );
    }

    // If the Writer has been used, and the service method we are calling for
    //   the include uses the OutputStream, the output will may be out of
    //   sequence. 
    response.getWriter().flush();
    
    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((HttpServletRequest) request
        ,servletName
        ,RequestSource.INCLUDE
        ,context.getServer()
        );
    DispatchServerResponse dispatchResponse
      =new DispatchServerResponse
        ((HttpServletResponse) response
        ,context.getServer()
        );
    
    
    context.service(dispatchRequest,dispatchResponse);
    
  }

  public void forward(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    if (context.isDebug())
    { 
      log.fine
        ("Forwarding to servlet "+servletName+" in "
        +((HttpServletRequest) request).getRequestURL()
        );
    }


    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((HttpServletRequest) request
        ,servletName
        ,RequestSource.FORWARD
        ,context.getServer()
        );

    context.service(dispatchRequest,(HttpServerResponse) response);
    
  }
}