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

import spiralcraft.util.IteratorEnumeration;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;

import javax.servlet.ServletInputStream;

import java.security.Principal;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UnsupportedEncodingException;


/**
 * Concrete HttpServletRequest implementation for internal dispatch request
 */
public class DispatchServerRequest
  extends AbstractHttpServletRequest
	implements HttpServletRequest
{
  
  private final HttpServletRequest containingRequest;

	public DispatchServerRequest
    (HttpServletRequest containingRequest
    ,String uri
    ,RequestSource source
    ,HttpServer server
    )
  { 
    this.containingRequest=containingRequest;
    setHttpServer(server);
    start();
    updateURI(uri);
    
    boolean original=true; 
    if (containingRequest.getAttribute("javax.servlet.include.request_uri")
         !=null
       )
    {
      original=false;
      copyAttributes
        ("javax.servlet.include.request_uri"
        ,"javax.servlet.include.context_path"
        ,"javax.servlet.include.servlet_path"
        ,"javax.servlet.include.path_info"
        ,"javax.servlet.include.query_string"
        );
    }
    
    if (containingRequest.getAttribute("javax.servlet.forward.request_uri")
         !=null
       )
    {
      original=false;
      copyAttributes
        ("javax.servlet.forward.request_uri"
        ,"javax.servlet.forward.context_path"
        ,"javax.servlet.forward.servlet_path"
        ,"javax.servlet.forward.path_info"
        ,"javax.servlet.forward.query_string"
        );
    }

    
    if (original)
    {
      String nature=(source==RequestSource.FORWARD?"forward":"include");
      setAttribute
        ("javax.servlet."+nature+".request_uri"
        ,containingRequest.getRequestURI()
        );
      setAttribute
        ("javax.servlet."+nature+".context_path"
        ,containingRequest.getContextPath()
        );
      setAttribute
        ("javax.servlet."+nature+".servlet_path"
        ,containingRequest.getServletPath()
        );
      setAttribute
        ("javax.servlet."+nature+".path_info"
        ,containingRequest.getPathInfo()
        );
      setAttribute
        ("javax.servlet."+nature+".query_string"
        ,containingRequest.getQueryString()
        );
      
    }
    this._source=source;
    
  }

	private void copyAttributes(String ... names)
	{
	  for (String name:names)
	  { setAttribute(name,containingRequest.getAttribute(name));
	  }
	}
	
	@Override
	public Object getAttribute(String name)
	{
    Object ret=super.getAttribute(name);
    if (ret==null)
    { ret=containingRequest.getAttribute(name);
    }
    return ret;
	}
	
  public Principal getUserPrincipal()
  { return containingRequest.getUserPrincipal();
  }

  public boolean isUserInRole(String role)
  { return containingRequest.isUserInRole(role);
  }

  @Override
  public Enumeration<?> getLocales()
  { return containingRequest.getLocales();
  }

  public Locale getLocale()
  { return containingRequest.getLocale();
  }


	public String getAuthType()
	{ return containingRequest.getAuthType();
	}
	
  public boolean isSecure()
  { return containingRequest.isSecure();
  }

  public Enumeration<?> getHeaders(String name)
  { return containingRequest.getHeaders(name);
  }

  
  @Override
  public String getParameter(String name)
  { 
    String ret=super.getParameter(name);
    if (ret==null)
    { ret=containingRequest.getParameter(name);
    }
    return ret;
  }
  
  @SuppressWarnings("unchecked")
  @Override
  public Enumeration<String> getParameterNames()
  { 
    HashSet<String> names=new HashSet<String>();

    Enumeration<String> originalParameterNames=super.getParameterNames();
    while (originalParameterNames.hasMoreElements())
    { names.add(originalParameterNames.nextElement());
    }
    
    Enumeration<String> parameterNames=containingRequest.getParameterNames();
    while (parameterNames.hasMoreElements())
    { names.add(parameterNames.nextElement());
    }
    
    return new IteratorEnumeration<String>(names.iterator());
  }
  
  @Override
  public String[] getParameterValues(String name)
  {
    String[] ret=super.getParameterValues(name);
    if (ret==null || ret.length==0)
    { ret=containingRequest.getParameterValues(name);
    }
    return ret;
  }
  
  @Override
  public Map<String,String[]> getParameterMap()
  {
    Map<String,String[]> map=new HashMap<String,String[]>();
    Enumeration<String> names=getParameterNames();
    while (names.hasMoreElements())
    { 
      String name=names.nextElement();
      map.put(name, getParameterValues(name));
    }
    return map;
  }
  
	public Cookie[] getCookies()
  { return containingRequest.getCookies();
	}
	
	public long getDateHeader(String name)
    throws IllegalArgumentException
	{ return containingRequest.getDateHeader(name);
	}
	
	public String getHeader(String name)
	{ return containingRequest.getHeader(name);
	}
	
	public Enumeration<?> getHeaderNames()
	{ return containingRequest.getHeaderNames();
	}
	
	public int getIntHeader(String name)
	{ return containingRequest.getIntHeader(name);
  }
	
	public String getMethod()
	{ return containingRequest.getMethod();
	}
	
	
	public String getRemoteUser()
	{ return containingRequest.getRemoteUser();
	}
	
	public String getRequestedSessionId()
	{ return containingRequest.getRequestedSessionId();
	}

	

	
	public HttpSession getSession(boolean create)
	{ return containingRequest.getSession(create);
	}
	
	public HttpSession getSession()
	{ return containingRequest.getSession();
	}


	public boolean isRequestedSessionIdFromCookie()
	{ return containingRequest.isRequestedSessionIdFromCookie();
	}

  /** 
   *@deprecated
   */
	@Deprecated
  public boolean isRequestedSessionIdFromUrl()
	{ return containingRequest.isRequestedSessionIdFromUrl();
	}

	public boolean isRequestedSessionIdFromURL()
	{ return containingRequest.isRequestedSessionIdFromURL();
	}

	public boolean isRequestedSessionIdValid()
	{ return containingRequest.isRequestedSessionIdValid();
	}
	


	public ServletInputStream getInputStream()
    throws IOException
	{ return containingRequest.getInputStream();
	}
	
	@Override
  public byte[] getRawRemoteAddress()
  { 
	  if (containingRequest instanceof AbstractHttpServletRequest)
	  { return ((AbstractHttpServletRequest) containingRequest)
	      .getRawRemoteAddress();
	  }
	  else
	  { 
	    String[] ip=getRemoteAddr().split("\\.");
	    byte[] bytes=new byte[4];
	    for (int i=0;i<4;i++)
	    { bytes[i]=Short.valueOf(ip[i]).byteValue();
	    }
	    return bytes;
	  }
  }
  
	public String getProtocol()
	{ return containingRequest.getProtocol();
	}
	
	public BufferedReader getReader()
    throws IOException
	{ return containingRequest.getReader();
	}	
	
	public String getRemoteAddr()
	{ return containingRequest.getRemoteAddr();
	}
	
//	public byte[] getRawRemoteAddress()
//	{ return containingRequest.getRawRemoteAddress();
//	}

	public String getRemoteHost()
	{ return containingRequest.getRemoteHost();
	}
	
	public String getScheme()
	{ return containingRequest.getScheme();
 	}
 	
 	public String getServerName()
 	{ return containingRequest.getServerName();
 	} 
 	
 	public int getServerPort()
 	{ return containingRequest.getServerPort();
 	}

 	public int getLocalPort()
  { return containingRequest.getLocalPort();
  }
 	  
  public String getLocalName()
  { return containingRequest.getLocalName();
  }

  public String getLocalAddr()
  { return containingRequest.getLocalAddr();
  }
  
  public int getRemotePort()
  { return containingRequest.getRemotePort();
  }
  

  public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException
  { containingRequest.setCharacterEncoding(arg0);    
  }
  
}
