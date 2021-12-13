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

public class ServerRequestDispatcher
  implements RequestDispatcher
{
 
  private final static ClassLog log
    =ClassLog.getInstance(ServerRequestDispatcher.class);
  
  private final HttpServiceContext context;
  private final String uri;
  
  public ServerRequestDispatcher(HttpServiceContext context,String uri)
  { 
    this.context=context;
    this.uri=uri;
  }
  
  
  @Override
  public void include(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    if (context.isDebug())
    { 
      log.fine
        ("Including "+uri+" in "
        +((HttpServletRequest) request).getRequestURL()
        );
    }

    // If the Writer has been used, and the service method we are calling for
    //   the include uses the OutputStream, the output may be out of
    //   sequence. 
    response.getWriter().flush();
    
    
    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((HttpServletRequest) request
        ,uri
        ,RequestSource.INCLUDE
        ,context.getDebugSettings()
        );
    DispatchServerResponse dispatchResponse
      =new DispatchServerResponse
        ((HttpServletResponse) response
        ,context.getDebugSettings()
        );

     
    context.service(dispatchRequest,dispatchResponse);
    
  }

  @Override
  public void forward(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    if (context.isDebug())
    { log.fine("Forwarding to "+uri);
    }

    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((HttpServletRequest) request
        ,uri
        ,RequestSource.FORWARD
        ,context.getDebugSettings()
        );

    context.service(dispatchRequest,(HttpServletResponse) response);
    response.getWriter().flush();
    response.getOutputStream().flush();
    
  }
  
  public void sendError
    (ServletRequest request
    ,HttpServerResponse response
    ,int status
    ,String message
    ,Throwable exception
    )
    throws ServletException,IOException
  {
  
    if (context.isDebug())
    {
      log.fine
        ("Forwarding error to uri "+uri+" in "
        +((HttpServletRequest) request).getRequestURL()
        );
      
    }
  
    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
      ((HttpServletRequest) request
      ,uri
      ,RequestSource.ERROR
      ,context.getDebugSettings()
      );
    
//    DispatchServerResponse dispatchResponse
//      =new DispatchServerResponse
//      ((HttpServletResponse) response
//      ,context.getServer()
//      );

    dispatchRequest.setAttribute
      ("javax.servlet.error.status_code",status);
    if (exception!=null)
    {
      dispatchRequest.setAttribute
        ("javax.servlet.error.exception_type",exception.getClass());
    }
    dispatchRequest.setAttribute
      ("javax.servlet.error.message",message);
    dispatchRequest.setAttribute
      ("javax.servlet.error.exception",exception);
    dispatchRequest.setAttribute
      ("javax.servlet.error.request_uri"
      ,((HttpServletRequest) request).getRequestURI()
      );
    dispatchRequest.setAttribute
      ("javax.servlet.error.servlet_name"
      ,request.getAttribute(ServletHolder.SERVLET_NAME_ATTRIBUTE)
      );

    response.setStatus(status);

    DispatchServerResponse dispatchResponse
      =new DispatchServerResponse
        ((HttpServletResponse) response
        ,context.getDebugSettings()
        );
    
    context.service(dispatchRequest,dispatchResponse);

  }
}