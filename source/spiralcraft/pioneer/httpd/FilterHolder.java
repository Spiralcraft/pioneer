//
// Copyright (c) 1998,2009 Michael Toth
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

import javax.servlet.Filter;
import javax.servlet.ServletException;


import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.Properties;

import spiralcraft.data.persist.AbstractXmlObject;

import spiralcraft.pioneer.util.ThrowableUtil;
import spiralcraft.vfs.Resolver;

/**
 * Loads and manages a Filter instance
 */
public class FilterHolder
{
  private Filter _filter;
  private String _filterClass;
  private ServletException _servletException;
  private HttpServiceContext _serviceContext;
  private Properties _initParams=new Properties();
  private boolean _loadAtStartup=false;
  private String _filterName;
  private URI dataURI;

  /**
   * Create a new Bean configured FilterHolder
   */
  public FilterHolder()
  {
  }
  
  public void setDataURI(URI dataURI)
  { this.dataURI=dataURI;
  }
  
  /**
   * Create new ServletHolder from a pre-configured Servlet
   */
  public FilterHolder(Filter filter)
  { _filter=filter;
  }
  
  
  public Filter getFilter()
    throws ServletException
  {
    if (_filter==null)
    { 
      if (_filterClass!=null || dataURI!=null)
      { load();
      }
      else
      { throw new ServletException("No Filter class or XML object URI specified");
      }
    }

    if (_servletException!=null)
    { throw _servletException;
    }
    else
    { return _filter;
    }
  }

  public void setServiceContext(HttpServiceContext context)
  { _serviceContext=context;
  }

  public void setFilterClass(String className)
  { 
    _filterClass=className;
    if (_filterName==null)
    { _filterName=className;
    }
  }
  
  public String getFilterName()
  { return _filterName;
  }
  
  public void setFilterName(String name)
  { _filterName=name;
  }

  public void setInitParameter(String name,String value)
  {
    if (_initParams==null)
    { _initParams=new Properties();
    }
    _initParams.put(name,value);
  }
  
  public void setInitParameters(Properties params)
  { _initParams=params;
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
    if (_filter!=null)
    { return;
    }

    try
    {
      Filter filter;
      
      if (dataURI!=null)
      { 
        if (Resolver.getInstance().resolve(dataURI).exists())
        {
          filter=AbstractXmlObject.<Filter>create
            (_filterClass==null
              ?null
              :AbstractXmlObject.typeFromClass(Class.forName(_filterClass))
            ,dataURI
            ,null
            ,null
            ).get();
        }
        else
        { throw new ServletException("Servlet data resource not found "+dataURI);
        }
      }
      else
      { 
        filter
          =(Filter) Class.forName
            (_filterClass
            ,true
            ,Thread.currentThread().getContextClassLoader()
            ).newInstance();
      }
      filter.init(new SimpleFilterConfig(_filterName,_serviceContext,_initParams));  
      _filter=filter;
    }
    catch (ServletException x)
    { _servletException=x;
    }
    catch (Exception x)
    { _servletException=new ServletException(x.toString()+"\r\n"+ThrowableUtil.getStackTrace(x));
    }
  }

}
