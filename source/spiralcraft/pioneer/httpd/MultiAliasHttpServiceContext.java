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

import javax.servlet.http.HttpServletResponse;

import spiralcraft.pioneer.log.Log;

import spiralcraft.pioneer.io.Filename;


public class MultiAliasHttpServiceContext
  extends SimpleHttpServiceContext
{
  
  private static final String DEBUG_GROUP
    =MultiAliasHttpServiceContext.class.getName();

  @Override
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
      HttpServiceContext subContext=_aliasMap.get(alias);
      if (subContext!=null)
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


  @Override
  public void init()
  {
    if (_aliasMap!=null)
    { resolveAliasMap();
    }
    super.init();
  }

  public void setAliasMap(HashMap<String,HttpServiceContext> aliasMap)
  { _aliasMap=aliasMap;
  }

  protected void resolveAliasMap()
  {
    Iterator<String> it=_aliasMap.keySet().iterator();
    while (it.hasNext())
    {
      String key= it.next();
      HttpServiceContext context=_aliasMap.get(key);
      context.setParentContext(this);
      if (getAlias()!=null)
      { context.setAlias(new Filename(getAlias(),key).toString());
      }
      else
      { context.setAlias(key);
      }
    }
    if (_log.isLevel(Log.DEBUG))
    { _log.log(Log.DEBUG,"aliasMap="+_aliasMap.keySet());
    }
  }

  private HashMap<String,HttpServiceContext> _aliasMap
    =new HashMap<String,HttpServiceContext>();
}


