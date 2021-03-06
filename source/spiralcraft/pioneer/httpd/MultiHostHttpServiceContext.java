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
/**
 * Service context that delegates the request
 *   to an appropriate subcontext for specific
 *   host headers.
 */
package spiralcraft.pioneer.httpd;

import java.util.HashMap;
import java.util.LinkedList;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletResponse;

import spiralcraft.common.ContextualException;
import spiralcraft.common.LifecycleException;
import spiralcraft.log.Level;

import spiralcraft.text.CaseInsensitiveString;

public class MultiHostHttpServiceContext
  extends SimpleHttpServiceContext
{
  
  private HashMap<CaseInsensitiveString,HttpServiceContext> _hostMap
    =new HashMap<CaseInsensitiveString,HttpServiceContext>();
  
  private LinkedList<HttpServiceContext> _hostList
    =new LinkedList<HttpServiceContext>();
  
  private HttpServiceContext _defaultServiceContext;

  
  @Override
  public void service(AbstractHttpServletRequest request,HttpServletResponse response)
    throws IOException
          ,ServletException
  { 
    String host=request.getHeader("Host");

    if (host!=null)
    {
      int portPos=host.indexOf(":");
      if (portPos>=0)
      { host=host.substring(0,portPos);
      }
      HttpServiceContext subContext
        =_hostMap.get(new CaseInsensitiveString(host));
      if (subContext!=null)
      { 
        if (debug)
        { log.log(Level.DEBUG,"Delegating to subcontext for "+host);
        }
        subContext.service(request,response);
      }
      else
      { 
        if (_defaultServiceContext==null)
        { super.service(request,response);
        }
        else
        { _defaultServiceContext.service(request,response);
        }
      }
    }
    else
    { 
      if (_defaultServiceContext==null)
      { super.service(request,response);
      }
      else
      { _defaultServiceContext.service(request,response);
      }
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
    super.start();    
    for (HttpServiceContext context : _hostList)
    { 
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

    for (HttpServiceContext context : _hostList)
    { context.stop();
    }
    super.stop();
  }

  public void setHostMappings(HostMapping[] hostMappings)
  {
    for (HostMapping mapping: hostMappings)
    { 
      String primaryHostName=null;
      for (String hostName : mapping.getHostNames())
      { 
        if (primaryHostName==null)
        { primaryHostName=hostName;
        }
        _hostMap.put(new CaseInsensitiveString(hostName),mapping.getContext());
      }
      _hostList.add(mapping.getContext());
      mapping.getContext().setParentContext(this);
      mapping.getContext().setVirtualHostName(primaryHostName);
    }
    
  }
  
  public void setDefaultServiceContext(HttpServiceContext context)
  { 
    _defaultServiceContext=context;
    _defaultServiceContext.setParentContext(this);
  }

}


