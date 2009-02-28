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

import java.util.List;
import java.util.Map;
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

import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;
import spiralcraft.net.mime.ContentTypeHeader;

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
  private static final ClassLog log
    =ClassLog.getInstance(AbstractHttpServletRequest.class);
  
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

    @Override
    public String toString()
    { return name+"="+value;
    }
	}

	protected HttpServiceContext _context;
	protected String _pathInfo;
	protected String _pathTranslated;
	protected String _queryString;
	protected String _requestURI;
  protected VariableManager _query;
  protected VariableManager _post;
	protected String _servletPath;
	protected String _contextPath;
  protected final HashMap<String,Object> _attributes
    =new HashMap<String,Object>();
  protected long _startTime;	
  protected HttpServer _httpServer;
  protected String _characterEncoding;

  protected ClassLog _log
    =ClassLog.getInstance(AbstractHttpServletRequest.class);

  public HttpServer getHttpServer()
  { return _httpServer;
  }
  
  public void setHttpServer(HttpServer server)
  { _httpServer=server;
  }  
  /**
   * Initialize before use
   */
  public void start()
  { 
    _context=null;
    _pathInfo=null;
    _pathTranslated=null;
    _queryString=null;
    _requestURI=null;
    _query=null;
    _post=null;
    _servletPath="";
    _contextPath="";
	  _attributes.clear();
    _startTime=Clock.instance().approxTimeMillis();

  }

  public void cleanup()
  { _attributes.clear();
  }
  
  public abstract byte[] getRawRemoteAddress();
  
  public void setServiceContext(HttpServiceContext context)
  { _context=context;
  }

  public RequestDispatcher getRequestDispatcher(String uri)
  { 
    URI absoluteURI=URI.create(getContextPath()+getServletPath());
    return _context.getRequestDispatcher(absoluteURI.resolve(uri).toString());
  }

  public String getContextPath()
  { 
    if (_httpServer.getDebugAPI())
    { log.fine(_contextPath);
    }
    return _contextPath;
  }

	public String getPathTranslated()
	{
		if (_pathTranslated==null)
		{ _pathTranslated=_context.getRealPath(_pathInfo);
		}
    if (_httpServer.getDebugService())
    { log.fine(_pathTranslated);
    }
		return _pathTranslated;
	}
  
	public String getPathInfo()
	{ 
	  
    if (_httpServer.getDebugAPI())
    { log.fine(_pathInfo);
    }
	  return _pathInfo;
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
	@SuppressWarnings("deprecation")
  @Deprecated
  public String getRealPath(String alias)
	{ return _context.getRealPath(alias);
	}
  
	/**
	 * <p>The Servlet path relative to this context, starting with a "/", or
	 *   "" if the Servlet is the default servlet for the root context (ie.
	 *   mapped to "/**")
	 * </p>
	 */
	public String getServletPath()
	{ 
    if (_httpServer.getDebugAPI())
    { log.fine(_servletPath);
    }
	  
	  return _servletPath;
	} 

	public String getRequestURI()
	{ 
	  
    if (_httpServer.getDebugAPI())
    { log.fine(_requestURI);
    }
	  return _requestURI;
	}
  
  /**
   * Reset the URI 
   */
  public void updateURI(String uri)
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
  public void updateServletPath(String servletPath)
  { 
    _servletPath=servletPath;
    calcPathInfo();
  }

  /**
   * <p>The portion of the request URI that indicates the context of the
   * request. The context path always comes first in a request URI. 
   * </p>
   * 
   * <p>The path starts with a "/" character but does not end with a "/" 
   * character.  For servlets in the default (root) context, this method 
   * returns "".
   * </p>
   * 
   */
  
  public void updateContextPath(String contextPath)
  { 
    _contextPath=contextPath;
    calcPathInfo();
  
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
	
	@Override
  public Map<String,String[]> getParameterMap()
  {
    Map<String,String[]> map=new HashMap<String,String[]>();
    for (String name : getParameterNameList())
    { map.put(name, getParameterValues(name));
    }
    return map;
  }
  
  protected List<String> getParameterNameList()
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
    return names;
    
  }
  
	public Enumeration<String> getParameterNames()
	{ return new IteratorEnumeration<String>(getParameterNameList().iterator());
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
	{ 
	  Object ret=_attributes.get(name);
	  if (_httpServer.getDebugService())
	  { log.fine(name +" = "+ ret);
	  }
	  return ret;
	}

  public Enumeration<String> getAttributeNames()
  { return new IteratorEnumeration<String>(_attributes.keySet().iterator());
  }

  public void setAttribute(String name,Object value)
  { 
    if (_httpServer.getDebugService())
    { log.fine(name +" = "+ value);
    }
    
    Object oldval=_attributes.remove(name);
    if (oldval!=null)
    { 
      _attributes.put(name,value);
      if (_context!=null)
      { _context.fireRequestAttributeReplaced(this,name,oldval);
      }
    }
    else
    {
      _attributes.put(name,value);
      if (_context!=null)
      { _context.fireRequestAttributeAdded(this,name,value);
      }
    }
  }

  public void removeAttribute(String name)
  { 
    Object oldval=_attributes.remove(name);
    if (oldval!=null && _context!=null)
    { _context.fireRequestAttributeRemoved(this,name,oldval);
    }
  }
  
	public int getContentLength()
	{ return getIntHeader("Content-Length");
	}
	
	public String getContentType()
	{ return getHeader("Content-Type");
	}
	
  public String getCharacterEncoding()
  { 
    if (_characterEncoding==null)
    { 
      String header=getHeader("Content-Type");
      try
      {
        if (header!=null)
        { 
          ContentTypeHeader parsed=new ContentTypeHeader("Content-Type",header);
          String encoding=parsed.getParameter("charset");
          if (encoding!=null)
          { _characterEncoding=encoding;
          }
        }
      }
      catch (IOException x)
      { log.log(Level.WARNING,"Error parsing Content-Type: "+header,x);
      }

    }
    return _characterEncoding;
  }
  
  public StringBuffer getRequestURL()
  {
    StringBuffer buf=new StringBuffer();
    buf.append(getScheme()).append("://");
    buf.append(getServerName());
    if (   (!isSecure() && getServerPort()!=80)
        || (isSecure() && getServerPort()!=443)
       )
    { buf.append(":").append(getServerPort());
    }
    buf.append(getRequestURI());
    if (getQueryString()!=null)
    { buf.append("?").append(getQueryString());
    }
    return buf;
  }  
  
	protected void calcPathInfo()
	{ 
	  _pathInfo=_requestURI.substring
	    (_contextPath.length()+_servletPath.length());
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
          && getContentType()
            .equalsIgnoreCase("application/x-www-form-urlencoded")
         )
      { 
        try
        {
          _post=VariableManager.fromStream
                (getContentLength()
                ,getInputStream()
                );
          if (_log.canLog(Level.DEBUG))
          { _log.log(Level.DEBUG,"Read post: ["+_post.toString()+"]");
          }
        }
        catch (IOException x)
        { 
          _log.log(Level.INFO,"IOException reading post: "+x.toString());
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

