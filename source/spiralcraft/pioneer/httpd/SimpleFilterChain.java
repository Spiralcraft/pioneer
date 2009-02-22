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
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;


import java.io.IOException;
import java.io.PrintStream;

import spiralcraft.exec.ExecutionContext;
import spiralcraft.log.ClassLog;

/**
 * Loads and manages a Filter instance
 */
public class SimpleFilterChain
  implements FilterChain
{
  private static final ClassLog log
    =ClassLog.getInstance(SimpleFilterChain.class);
  
  private Filter _filter;
  private FilterChain _next;
  private HttpServiceContext context;

  /**
   * Create a new FilterChainImpl
   */
  public SimpleFilterChain()
  {
  }
  
  public void setContext(HttpServiceContext context)
  { this.context=context;
  }
  
  
  /**
   * Create a new FilterChain link for a Filter
   */
  public SimpleFilterChain(Filter filter)
  { _filter=filter;
  }
  
  /**
   * Specify the next link in the FilterChain
   * 
   * @param next
   */
  public void setNext(FilterChain next)
  { this._next=next;
  }
  
  public Filter getFilter()
    throws ServletException
  { return _filter;
  }

  public void doFilter
    (ServletRequest request
    , ServletResponse response
    )
    throws IOException, ServletException
  { 
    if (context.isDebug())
    { log.fine(((HttpServletRequest) request).getRequestURI()+" -> "+getFilter());
    }
    try
    { getFilter().doFilter(request,response,_next);
    }
    catch (ServletException x)
    { 
      PrintStream err=ExecutionContext.getInstance().err();
      x.printStackTrace(err);
      if (x.getRootCause()!=null)
      { x.getRootCause().printStackTrace(err);
      }
      throw x;
    }
  }

}