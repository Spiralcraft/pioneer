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
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;

public class ServerRequestDispatcher
  implements RequestDispatcher
{
 
  private final HttpServiceContext context;
  private final String uri;
  
  public ServerRequestDispatcher(HttpServiceContext context,String uri)
  { 
    this.context=context;
    this.uri=uri;
  }
  
  public void include(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    // System.out.println("Dispatch- including ["+uri+"]");

    // If the Writer has been used, and the service method we are calling for
    //   the include uses the OutputStream, the output will may be out of
    //   sequence. 
    response.getWriter().flush();
    
    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((AbstractHttpServletRequest) request
        ,uri
        );
    DispatchServerResponse dispatchResponse
      =new DispatchServerResponse((HttpServletResponse) response);
    
    
    context.service(dispatchRequest,dispatchResponse);
    
  }

  public void forward(ServletRequest request,ServletResponse response)
    throws ServletException,IOException
  {
    // System.out.println("Dispatch- forwarding ["+uri+"]");

    DispatchServerRequest dispatchRequest
      =new DispatchServerRequest
        ((AbstractHttpServletRequest) request
        ,uri
        );

    context.service(dispatchRequest,(HttpServerResponse) response);
    
  }
}