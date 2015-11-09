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

import spiralcraft.common.Lifecycle;
import spiralcraft.data.persist.AbstractXmlObject;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.servlet.kit.StandardFilterConfig;
import spiralcraft.vfs.Resolver;

/**
 * Loads and manages a Filter instance
 */
public class FilterHolder
  implements Lifecycle
{

  private static final ClassLog log
    =ClassLog.getInstance(FilterHolder.class);
  
  private Filter _filter;
  private String _filterClass;
  private ServletException _servletException;
  private HttpServiceContext _serviceContext;
  private Properties _initParams=new Properties();
  private boolean _loadAtStartup=false;
  private String _filterName;
  private URI dataURI;
  private boolean _loaded;
  

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
    if (!_loaded)
    { 
      if (_filterClass!=null || dataURI!=null || _filter!=null)
      { load();
      }
      else
      { throw new ServletException("No Filter class, XML object URI, or Filter instance specified");
      }
    }

    if (_servletException!=null)
    { throw _servletException;
    }
    else
    { return _filter;
    }
  }
  
  /**
   * Provide a pre-configured filter instance
   * 
   * @param filter
   */
  public void setFilterInstance(Filter filter)
  { this._filter=filter;
  }

  public void setServiceContext(HttpServiceContext context)
  { _serviceContext=context;
  }

  public String getFilterClass()
  { return _filterClass;
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

  public void setLoadAtStartup(boolean val)
  { _loadAtStartup=val;
  }

  @Override
  public void start()
  {
    if (_loadAtStartup)
    { load();
    }
  }

  @Override
  public void stop()
  {
    if (_filter!=null)
    { _filter.destroy();
    }
    if (dataURI!=null || _filter!=null)
    { _filter=null;
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
            ).get();
        }
        else
        { throw new ServletException("Filter data resource not found "+dataURI);
        }
      }
      else if (_filterClass!=null)
      { 
        filter
          =(Filter) Class.forName
            (_filterClass
            ,true
            ,Thread.currentThread().getContextClassLoader()
            ).newInstance();
      }
      else
      {
      	// Use provided instance
      	filter=_filter;
      }
      if (_serviceContext.isDebug())
      { log.fine("Initializing filter "+_filterName+" with "+_initParams);
      }
      filter.init(new StandardFilterConfig(_filterName,_serviceContext,_initParams));  
      _filter=filter;
    }
    catch (ServletException x)
    { 
    	_servletException=x;
      log.log(Level.WARNING,"Error initializing filter "+_filterName,x);
    }
    catch (Exception x)
    { 
      log.log(Level.WARNING,"Error initializing filter "+_filterName,x);
    	_servletException=new ServletException(x.toString(),x);
    }
    _loaded=true;
  }

}
