//
// Copyright (c) 1998,2007 Michael Toth
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

import java.util.TimeZone;
import java.util.Iterator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Enumeration;

import java.io.IOException;

import java.net.URI;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import javax.servlet.http.HttpServletRequest;

import javax.servlet.RequestDispatcher;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.time.Clock;


import spiralcraft.pioneer.servlet.VariableManager;

import spiralcraft.util.IteratorEnumeration;


/** 
 * Abstract HttpServletRequest implementation for top level or dispatched
 *   requests
 */
public abstract class AbstractHttpServletRequest
  implements HttpServletRequest
{
  protected static final VariableManager NULL_FORM=new VariableManager();

  protected final DateFormat _rfc1123HeaderDateFormat
    =new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
  { _rfc1123HeaderDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  protected final DateFormat[] _altDateFormats
    ={ new SimpleDateFormat("EEEE, dd-MMM-yy HH:mm:ss 'GMT'")
     , new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy")
     };
  {
    _altDateFormats[0].setTimeZone(TimeZone.getTimeZone("GMT"));
    _altDateFormats[1].setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  
	protected class Variable
	{
		public String name;
		public String value;

    public String toString()
    { return name+"="+value;
    }
	}

	protected HttpServiceContext _server;
  protected String _alias;
	protected String _pathInfo;
	protected String _pathTranslated;
	protected String _queryString;
	protected String _requestURI;
  protected VariableManager _query;
  protected VariableManager _post;
	protected String _servletPath;
  protected final HashMap<String,Object> _attributes
    =new HashMap<String,Object>();
  protected long _startTime;	

  protected Log _log=LogManager.getGlobalLog();

  /**
   * Initialize before use
   */
  public void start()
  { 
    _server=null;
    _alias=null;
    _pathInfo=null;
    _pathTranslated=null;
    _queryString=null;
    _requestURI=null;
    _query=null;
    _post=null;
    _servletPath="";
	  _attributes.clear();
    _startTime=Clock.instance().approxTimeMillis();

  }

  public void cleanup()
  { _attributes.clear();
  }
  
  public abstract byte[] getRawRemoteAddress();
  
  public void setServiceContext(HttpServiceContext context)
  { _server=context;
  }

  public RequestDispatcher getRequestDispatcher(String uri)
  { 
    URI absoluteURI=URI.create(getRequestURI());
    return _server.getRequestDispatcher(absoluteURI.resolve(uri).toString());
  }

  public String getContextPath()
  { return "/"+_server.getAlias();
  }

	public String getPathTranslated()
	{
		if (_pathTranslated==null)
		{ _pathTranslated=_server.getRealPath(_pathInfo);
		}
		return _pathTranslated;
	}
  
	public String getPathInfo()
	{ return _pathInfo;
	}
	
	
	public String getQueryString()
	{ return _queryString;
	}

  /**
   * The start time of the request
   */
  public long getStartTime()
  { return _startTime;
  }

  /**
   *@deprecated
   */
	public String getRealPath(String alias)
	{ return _server.getRealPath(alias);
	}
  
	public String getServletPath()
	{ return _servletPath;
	} 

	public String getRequestURI()
	{ return _requestURI;
	}
  
  /**
   * Reset the URI 
   */
  public void setURI(String uri)
  { 
    _requestURI=uri;
    _pathTranslated=null;
    _pathInfo=null;
    calcPathInfo();
  }

  /**
   * Called by the server to specify the part of
   *   the URI that refers to the servlet being
   *   invoked.
   *
   * Once this information is supplied, derived
   *   properties such as pathTranslated() and
   *   pathInfo() can be determined.
   */
  public void setServletPath(String servletPath)
  { 
    _servletPath=servletPath;
    calcPathInfo();
  }
  
  public void setAlias(String alias)
  { _alias=alias;
  }

  public String getAlias()
  { return _alias;
  }

  
	public String getParameter(String name)
	{ 
    ensureParameters();
    String ret=_query.getValue(name);
    if (ret!=null)
    { return ret;
    } 
    return _post.getValue(name);
	}
	
	public Enumeration<String> getParameterNames()
	{ 
    ensureParameters();
    ArrayList<String> names=new ArrayList<String>();
    Iterator<String> it=_query.getNames();
    while (it.hasNext())
    { names.add(it.next());
    }
    it=_post.getNames();
    while (it.hasNext())
    { names.add(it.next());
    }
    
    return new IteratorEnumeration<String>(names.iterator());
	}
	
	public String[] getParameterValues(String name)
	{
    ensureParameters();
    String[] queryRet=_query.getList(name);
    String[] postRet=_post.getList(name);
    
    if (queryRet!=null && queryRet.length>0
        && (postRet==null || postRet.length==0)
       )
    { return queryRet;
    }
       
    if (postRet!=null && postRet.length>0
        && (queryRet==null || queryRet.length==0)
       )
    { return postRet;
    }
    
    if (queryRet!=null && postRet!=null)
    {
      String[] ret=new String[queryRet.length+postRet.length];
      System.arraycopy(queryRet,0,ret,0,queryRet.length);
      System.arraycopy(postRet,0,ret,queryRet.length,postRet.length);
      return ret;
    }

    return null;
	}

	public Object getAttribute(String name)
	{ return _attributes.get(name);
	}

  public Enumeration<String> getAttributeNames()
  { return new IteratorEnumeration<String>(_attributes.keySet().iterator());
  }

  public void setAttribute(String name,Object value)
  { _attributes.put(name,value);
  }

  public void removeAttribute(String name)
  { _attributes.remove(name);
  }
  
	public int getContentLength()
	{ return getIntHeader("Content-Length");
	}
	
	public String getContentType()
	{ return getHeader("Content-Type");
	}
	
  public String getCharacterEncoding()
  { return getHeader("Content-Encoding");
  }
  
	protected void calcPathInfo()
	{ _pathInfo=_requestURI.substring(_servletPath.length());
	}

  protected synchronized void ensureParameters()
  {
    ensureQuery();
    ensurePost();
  }

  protected synchronized void ensureQuery()
  {
    if (_query==null)
    { _query=VariableManager.fromQuery(this);
    }
  }

  private synchronized void ensurePost()
  {
    if (_post==null)
    {
      if (getContentType()!=null
          && getContentType().equalsIgnoreCase("application/x-www-form-urlencoded")
         )
      { 
        try
        {
          _post=VariableManager.fromStream
                (getContentLength()
                ,getInputStream()
                );
          if (_log.isDebugEnabled("spiralcraft.pioneer.httpd.protocol"))
          { _log.log(Log.DEBUG,"Read post: ["+_post.toString()+"]");
          }
        }
        catch (IOException x)
        { 
          _log.log(Log.MESSAGE,"IOException reading post: "+x.toString());
          _post=NULL_FORM;
        }
      }
      else
      { 
        // XXX Deal with multipart
        _post=NULL_FORM;
      }
    }
  }

  
}

