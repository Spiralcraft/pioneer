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
package spiralcraft.pioneer.httpd;



import spiralcraft.pioneer.log.Log;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;

import javax.servlet.ServletInputStream;

import java.security.Principal;

import java.util.Enumeration;
import java.util.Map;
import java.util.Locale;

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
  
  private final AbstractHttpServletRequest containingRequest;

	public DispatchServerRequest
    (AbstractHttpServletRequest containingRequest
    ,String uri
    )
  { 
    this.containingRequest=containingRequest;
    start();
    setURI(uri);
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
  @SuppressWarnings("deprecation")
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
  { return containingRequest.getRawRemoteAddress();
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

  public StringBuffer getRequestURL()
  {
    _log.log(Log.SEVERE,"getRequestURL() not implemented");
    // TODO Auto-generated method stub
    return null;
  }

  public Map<?,?> getParameterMap()
  {
    // TODO Auto-generated method stub
    _log.log(Log.SEVERE,"getParameterMap() not implemented");
    return null;
  }

  public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException
  {
    // TODO Auto-generated method stub
    _log.log(Log.SEVERE,"setCharacterEncoding() not implemented");
    
  }
  
}
