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
import javax.servlet.http.HttpServletRequest;


import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Properties;

import spiralcraft.common.Lifecycle;
import spiralcraft.data.persist.AbstractXmlObject;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.vfs.Resolver;

/**
 * Loads and manages a Servlet instance
 */
public class ServletHolder
  implements FilterChain,Lifecycle
{
  private static final ClassLog log
    =ClassLog.getInstance(ServletHolder.class);
    
  private Servlet _servlet;
  private String _servletClass;
  private ServletException _servletException;
  private HttpServiceContext _serviceContext;
  private Properties _initParams=new Properties();
  private int _loadOnStartup=-1;
  private String _servletName;
  private URI dataURI;
  private boolean _loaded;

  /**
   * Create a new Bean configured ServletHolder
   */
  public ServletHolder()
  {
  }
  
  public void setDataURI(URI dataURI)
  { this.dataURI=dataURI;
  }
  
  /**
   * Create new ServletHolder from a pre-configured Servlet
   */
  public ServletHolder(Servlet servlet)
  { _servlet=servlet;
  }
  
  /**
   * Provide a Servlet instance directly.
   * 
   * @param servlet
   */
  public void setServletInstance(Servlet servlet)
  { _servlet=servlet;
  }
  
  public Servlet getServlet()
    throws ServletException
  {
    if (!_loaded)
    { 
      if (_servletClass!=null || dataURI!=null || _servlet!=null)
      { load();
      }
      else
      { throw new ServletException
          ("No Servlet class, XML object URI, or Servlet instance specified");
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

  public void setInitProperties(Properties params)
  { _initParams=params;
  }
  
  public void setInitParameters(InitParameter[] parameters)
  {
    for (InitParameter param:parameters)
    { setInitParameter(param.getName(),param.getValue());
    }
  }
  
  public void setInitParametersAsText(String initParametersText)
  { 
    _initParams=new Properties();
    try
    { _initParams.load(new StringReader(initParametersText));
    }
    catch (IOException x)
    { throw new IllegalArgumentException(x);
    }
  }

  public void setInitParameter(String name,String value)
  {
    if (_initParams==null)
    { _initParams=new Properties();
    }
    _initParams.put(name,value);
  }
  
  public void setLoadAtStartup(boolean val)
  { _loadOnStartup=(val?0:-1);
  }

  public void setLoadOnStartup(int val)
  { _loadOnStartup=val;
  }
  
  public int getLoadOnStartup()
  { return _loadOnStartup;
  }
  
  @Override
  public void start()
  {
    if (_loadOnStartup>-1)
    { load();
    }
  }

  @Override
  public void stop()
  {
    if (_servlet!=null)
    { _servlet.destroy();
    }
    if (dataURI!=null || _servletClass!=null)
    { _servlet=null;
    }
    _loaded=false;
  }

  private synchronized void load()
  {
    if (_loaded)
    { return;
    }


    try
    {
      Servlet servlet;
      
      if (dataURI!=null)
      { 
        if (Resolver.getInstance().resolve(dataURI).exists())
        {
          servlet=AbstractXmlObject.<Servlet>create
            (_servletClass==null
              ?null
              :AbstractXmlObject.typeFromClass(Class.forName(_servletClass))
            ,dataURI
            ).get();
        }
        else
        { throw new ServletException("Servlet data resource not found "+dataURI);
        }
      }
      else if (_servletClass!=null)
      { 
        servlet
          =(Servlet) Class.forName
            (_servletClass
            ,true
            ,Thread.currentThread().getContextClassLoader()
            ).newInstance();
      }
      else
      { 
        // Use provided instance
        servlet=_servlet;
      }
      if (_serviceContext.isDebug())
      { log.fine("Initializing servlet "+_servletName+" with "+_initParams);
      }
      servlet.init(new SimpleServletConfig(_servletName,_serviceContext,_initParams));  
      _servlet=servlet;
    }
    catch (ServletException x)
    { 
      _servletException=x;
      log.log(Level.WARNING,"Error initializing servlet "+_servletName,x);
    }
    catch (Exception x)
    { 
      log.log(Level.WARNING,"Error initializing servlet "+_servletName,x);
      _servletException=new ServletException(x.toString(),x);
    }
    _loaded=true;
  }

  @Override
  public void doFilter
    (ServletRequest request
    , ServletResponse response
    )
    throws IOException, ServletException
  { 
    if (_serviceContext==null)
    { throw new IllegalStateException
        ("ServletContext is null in ServletHolder "+_servletName
            +" : "+_servletClass
         );
    }
    if (_serviceContext.isDebug())
    { 
      log.fine(((HttpServletRequest) request).getRequestURI()+" -> "
         +getServlet());
    }    
    try
    { getServlet().service(request,response);
    }
    catch (ServletException x)
    { 
      log.log
        (Level.WARNING
        ,"Error handling request "
          +((HttpServletRequest) request).getRequestURI()
        ,x
        );
      throw x;
    }
    catch (IOException x)
    {
      if (_serviceContext.isDebug())
      { 
        log.log
          (Level.DEBUG
          ,"IOException handling request "
            +((HttpServletRequest) request).getRequestURI()
          ,x
          );
      }
      throw x;
    }
  }

}
