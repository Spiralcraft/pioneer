//
// Copyright (c) 1998,2008 Michael Toth
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

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;


import java.io.IOException;
import java.util.Properties;
import spiralcraft.pioneer.util.ThrowableUtil;

/**
 * Loads and manages a Servlet instance
 */
public class ServletHolder
  implements FilterChain
{
  private Servlet _servlet;
  private String _servletClass;
  private ServletException _servletException;
  private HttpServiceContext _serviceContext;
  private Properties _initParams=new Properties();
  private boolean _loadAtStartup=false;
  private String _servletName;

  /**
   * Create a new Bean configured ServletHolder
   */
  public ServletHolder()
  {
  }
  
  /**
   * Create new ServletHolder from a pre-configured Servlet
   */
  public ServletHolder(Servlet servlet)
  { _servlet=servlet;
  }
  
  
  public Servlet getServlet()
    throws ServletException
  {
    if (_servlet==null)
    { 
      if (_servletClass!=null)
      { load();
      }
      else
      { throw new ServletException("No servlet class specified");
      }
    }

    if (_servletException!=null)
    { throw _servletException;
    }
    else
    { return _servlet;
    }
  }

  public void setServiceContext(HttpServiceContext context)
  { _serviceContext=context;
  }

  public void setServletClass(String className)
  { 
    _servletClass=className;
    if (_servletName==null)
    { _servletName=className;
    }
  }
  
  public String getServletName()
  { return _servletName;
  }
  
  public void setServletName(String name)
  { _servletName=name;
  }

  public void setInitParameters(Properties params)
  { _initParams=params;
  }

  public void setLoadAtStartup(boolean val)
  { _loadAtStartup=val;
  }

  public void init()
  {
    if (_loadAtStartup)
    { load();
    }
  }


  private synchronized void load()
  {
    if (_servlet!=null)
    { return;
    }

    try
    {
      Servlet servlet=(Servlet) Class.forName(_servletClass).newInstance();
      servlet.init(new SimpleServletConfig(_servletName,_serviceContext,_initParams));  
      _servlet=servlet;
    }
    catch (ServletException x)
    { _servletException=x;
    }
    catch (Exception x)
    { _servletException=new ServletException(x.toString()+"\r\n"+ThrowableUtil.getStackTrace(x));
    }
  }

  public void doFilter
    (ServletRequest request
    , ServletResponse response
    )
    throws IOException, ServletException
  { getServlet().service(request,response);
  }

}