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
import java.util.Iterator;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServletResponse;

import spiralcraft.pioneer.log.Log;

import spiralcraft.text.CaseInsensitiveString;

public class MultiHostHttpServiceContext
  extends SimpleHttpServiceContext
{
  
  private HashMap<CaseInsensitiveString,HttpServiceContext> _hostMap
    =new HashMap<CaseInsensitiveString,HttpServiceContext>();
  
  private HashMap _suppliedHostMap;
  private HttpServiceContext _defaultServiceContext;

  
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
      HttpServiceContext subContext=(HttpServiceContext) _hostMap.get(new CaseInsensitiveString(host));
      if (subContext!=null)
      { 
        if (_log.isDebugEnabled("com.spiralcraft.httpd.service"))
        { _log.log(Log.DEBUG,"Delegating to subcontext for "+host);
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

  public void init()
  {
    if (_suppliedHostMap!=null)
    {
      _hostMap=new HashMap();
    
      if (_log.isLevel(Log.DEBUG))
      { _log.log(_log.DEBUG,"HostMap="+_suppliedHostMap.keySet());
      }

      Iterator it=_suppliedHostMap.keySet().iterator();
      while (it.hasNext())
      {
        String key=(String) it.next();
        HttpServiceContext context=(HttpServiceContext) _suppliedHostMap.get(key);
        context.setParentContext(this);
        _hostMap.put(new CaseInsensitiveString(key),context);
      }
    }
    super.init();
  }

  /**
   * Deprecated 
   * @deprecated
   * @param hostMap
   */
  public void setHostMap(HashMap hostMap)
  { _suppliedHostMap=hostMap;
  }

  public void setHostMappings(HostMapping[] hostMappings)
  {
    for (HostMapping mapping: hostMappings)
    { 
      for (String hostName : mapping.getHostNames())
      { _hostMap.put(new CaseInsensitiveString(hostName),mapping.getContext());
      }
    }
    
  }
  
  public void setDefaultServiceContext(HttpServiceContext context)
  { 
    _defaultServiceContext=context;
    _defaultServiceContext.setParentContext(this);
  }

}


