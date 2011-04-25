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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletResponse;

import spiralcraft.common.ContextualException;
import spiralcraft.common.LifecycleException;
import spiralcraft.log.Level;

import spiralcraft.util.Path;

/**
 * Service context that delegates the request
 *   to an appropriate subcontext based on a specific 
 *   request prefix.
 */
public class MultiAliasHttpServiceContext
  extends SimpleHttpServiceContext
{

  private HashMap<String,HttpServiceContext> _aliasMap
    =new HashMap<String,HttpServiceContext>();
  
  private LinkedList<HttpServiceContext> _subcontextList
    =new LinkedList<HttpServiceContext>();  

  @Override
  public void service
    (AbstractHttpServletRequest request
    ,HttpServletResponse response
    )
    throws IOException
          ,ServletException
  { 
    boolean debugService=debug || request.getHttpServer().getDebugService();
    
    // Force pathInfo computation
    request.updateContextPath(getContextPath());
    
    // At this point, request.getServletPath() should be empty and
    //   request.getPathInfo() should contain everything after the
    //   context path
    
    String alias=null;
    if (request.getPathInfo()!=null)
    { 
      if (debugService)
      { log.log(Level.DEBUG,"request.getPathInfo() = "+request.getPathInfo());
      }
      alias=new Path(request.getPathInfo(),'/').firstElement();
    }
    
    
    if (alias!=null)
    {
      if (debugService)
      { log.log(Level.DEBUG,"Checking alias map for '"+alias+"'");
      }
      HttpServiceContext subContext=_aliasMap.get(alias);
      if (subContext!=null)
      {
        if (debugService)
        { log.log(Level.DEBUG,"Delegating to subcontext for alias "+alias);
        }
        subContext.service(request,response);
      }
      else
      { 
        if (debugService)
        { log.log(Level.DEBUG,"Nothing aliased to '"+alias+"'");
        }
        super.service(request,response);
      }
    }
    else
    { super.service(request,response);
    }
  }


  @Override
  /**
   * <p>Prepare the ServiceContext for request handling.
   * </p>
   * 
   * <p>Starts this RequestContext first, then children, so that children
   *   can inherit final configuration details
   * </p>
   */
  public void start()
    throws LifecycleException
  {
    if (_aliasMap!=null)
    { resolveAliasMap();
    }

    super.start();
    for (HttpServiceContext context : _subcontextList)
    { 
      context.setVirtualHostName(virtualHostName);
      try
      { context.bind(focus);
      }
      catch (ContextualException x)
      { throw new LifecycleException("Error binding ServletContext",x);
      }
      context.start();
    }
  }

  @Override
  public void stop()
    throws LifecycleException
  {
    for (HttpServiceContext context : _subcontextList)
    { context.stop();
    }
    super.stop();
  }

  public void setPathMappings(PathMapping[] pathMappings)
  {
    _aliasMap=new HashMap<String,HttpServiceContext>();
    for (PathMapping mapping: pathMappings)
    { _aliasMap.put(mapping.getName(),mapping.getContext());
    }
    
  }

  protected void resolveAliasMap()
  {
    Iterator<String> it=_aliasMap.keySet().iterator();
    while (it.hasNext())
    {
      String key= it.next();
      HttpServiceContext context=_aliasMap.get(key);
      context.setParentContext(this);
      context.setContextPath(getContextPath()+"/"+key);
      _subcontextList.add(context);
    }
    if (debug)
    { log.log(Level.DEBUG,"aliasMap="+_aliasMap.keySet());
    }
  }

}


