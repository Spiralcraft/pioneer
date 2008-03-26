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
 *   to an appropriate subcontext based on a specific 
 *   request prefix.
 */
package spiralcraft.pioneer.httpd;

import java.util.HashMap;
import java.util.Iterator;

import java.io.IOException;

import javax.servlet.ServletException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;

import spiralcraft.pioneer.log.Log;

import spiralcraft.pioneer.io.Filename;


public class MultiAliasHttpServiceContext
  extends SimpleHttpServiceContext
{
  
  private static final String DEBUG_GROUP
    =MultiAliasHttpServiceContext.class.getName();

  public void service(AbstractHttpServletRequest request,HttpServletResponse response)
    throws IOException
          ,ServletException
  { 
    String alias;
    if (getAlias()!=null)
    { 
      String relativeURI=new Filename(request.getRequestURI())
              .subtract(new Filename("/"+getAlias()));

      if (_log.isDebugEnabled(DEBUG_GROUP))
      { _log.log(Log.DEBUG,"RelativeURI="+relativeURI);
      }
      alias=new Filename(relativeURI).getFirstName();
    }
    else
    { alias=new Filename(request.getRequestURI()).getFirstName();
    }
    if (alias!=null)
    {
      if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
      { _log.log(Log.DEBUG,"Checking alias map for '"+alias+"'");
      }
      HttpServiceContext subContext=(HttpServiceContext) _aliasMap.get(alias);
      if (subContext!=null)
      {
        try
        {
          if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
          { _log.log(Log.DEBUG,"Delegating to subcontext for alias "+alias);
          }
          if (getAlias()!=null)
          { request.setAlias(getAlias()+"/"+alias);
          }
          else
          { request.setAlias(alias);
          }
          subContext.service(request,response);
        }
        finally
        {
          if (_accessLog!=null && (request instanceof HttpServerRequest))
          { _accessLog.log
              ((HttpServerRequest) request,(HttpServerResponse) response);
          }
        }
      }
      else
      { 
        if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
        { _log.log(Log.DEBUG,"Nothing aliased to "+alias);
        }
        super.service(request,response);
      }
    }
    else
    { super.service(request,response);
    }
  }


  public void init()
  {
    if (_aliasMap!=null)
    { resolveAliasMap();
    }
    super.init();
  }

  public void setAliasMap(HashMap aliasMap)
  { _aliasMap=aliasMap;
  }

  protected void resolveAliasMap()
  {
    Iterator it=_aliasMap.keySet().iterator();
    while (it.hasNext())
    {
      String key=(String) it.next();
      HttpServiceContext context=(HttpServiceContext) _aliasMap.get(key);
      context.setParentContext(this);
      if (getAlias()!=null)
      { context.setAlias(new Filename(getAlias(),key).toString());
      }
      else
      { context.setAlias(key);
      }
    }
    if (_log.isLevel(Log.DEBUG))
    { _log.log(_log.DEBUG,"aliasMap="+_aliasMap.keySet());
    }
  }

  private HashMap _aliasMap=new HashMap();
}


