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

import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.HashMap;
import java.util.Set;

import spiralcraft.net.ip.Subnet;
import spiralcraft.pioneer.util.MappedList;
import spiralcraft.pioneer.util.ListMap;
import spiralcraft.pioneer.util.Translator;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import spiralcraft.time.Clock;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;

import javax.servlet.ServletInputStream;

import java.security.Principal;

import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Locale;

import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.Charset;

import javax.net.ssl.SSLSocket;


import java.text.ParseException;

import spiralcraft.text.CaseInsensitiveString;
import spiralcraft.util.EmptyIterator;
import spiralcraft.util.IteratorEnumeration;


/**
 * Concrete HttpServletRequest implementation for top level (socket) request
 */
public class HttpServerRequest
  extends AbstractHttpServletRequest
	implements HttpServletRequest
{
  private static final ClassLog log
    =ClassLog.getInstance(HttpServerRequest.class);

	
	private HttpServerResponse _response;
  private HttpSession _session;
	private Cookie[] _cookies;
	private LinkedList<Cookie> _cookieList=new LinkedList<Cookie>();
	
  private String _requestLine;
	private String _method;
	private String _remoteUser;
	private String _requestedSessionId;
  private String _requestURL;
	private boolean _sessionFromCookie;
	private boolean _sessionFromUrl;

	private String _protocol;
	private String _remoteAddr;
  private byte[] _rawRemoteAddr;
  private String _remoteHost;
  private InetAddress _remoteInetAddress;
  
  private String _host;
  private int _port;
	private String _scheme="http";
	private boolean _secure;
	
	private final ServerInputStream _inputStream=new ServerInputStream();
	private ServletInputStream _apiInputStream;

	private BufferedReader _reader;
  private Socket _socket;
	private List<Locale> _locales=new LinkedList<Locale>();


  private Principal _userPrincipal;
	
	private MappedList _headers=new MappedList(new LinkedList<Variable>());
	private ListMap _headerMap
		=_headers.addMapView
			("name"
			,new HashMap<String,Variable>()
			,new Translator()
				{
					public Object translate(Object value)
					{ return new CaseInsensitiveString(((Variable) value).name);
					}
				}
			);
			
  
  private boolean _started;
  private boolean _headersRead;
  
  private boolean debugProtocol;
  private boolean debugAPI;

	public HttpServerRequest()
  {
  }





  public void setTraceStream(OutputStream traceStream)
  { _inputStream.setTraceStream(traceStream);
  }

  /**
   * Start a new request.
   * 
   * @param sock
   * @throws IOException
   */
  public void start(Socket sock)
    throws IOException
	{
    super.start();
    debugProtocol=_httpServer.getDebugProtocol();
    debugAPI=_httpServer.getDebugAPI();
    
    _started=false;
    _socket=sock;
    _secure=_socket instanceof SSLSocket;    
    _remoteInetAddress=sock.getInetAddress();
		_inputStream.start(sock.getInputStream());
    

    _session=null;
    _cookies=null;
    _cookieList.clear();
	
    _requestLine=null;
    _method=null;
    _remoteUser=null;
    _requestedSessionId=null;
    _requestURL=null;
    _sessionFromCookie=false;
    _sessionFromUrl=false;

    _protocol=null;
    _remoteAddr=null;
    _rawRemoteAddr=null;
    _remoteHost=null;
    _host=null;
    _port=0;
    _scheme="http";
    _reader=null;
	

    _headersRead=false;
	  _headers.clear();
    
    
    _requestLine=_inputStream.readAsciiLine();
    
    if (_requestLine!=null)
    {
      _started=true;
      _startTime=Clock.instance().approxTimeMillis();
      _httpServer.requestStarted();
      try
      { parseRequest();
      }
      catch (Exception x)
      { throw new IOException("Invalid request: "+_requestLine);
      }

      if (debugProtocol)
      { 
        _log.log(Level.DEBUG,">>> "+_method+" "+_requestURL
            +" "+_protocol); 
      }
    }
    else
    { throw new InterruptedIOException();
    }
	}

  public boolean wasStarted()
  { return _started;
  }

  /**
   * Ensure that any stray input data is discarded, after each request
   */
  public void finish()
  {
    _apiInputStream=null;
    try
    {
      if (_inputStream.getCount()<getContentLength())
      { 
        if (_httpServer.getDebugProtocol())
        { 
          log.fine
            ("Discarding "
            +(getContentLength()-_inputStream.getCount())
            +" bytes of unread unput"
            );
        }
        
        _inputStream.discard(getContentLength()-_inputStream.getCount());
      }
    }
    catch (IOException x)
    { _log.log(Level.DEBUG,">>> IOException draining input stream "+x);
    }
  }

  /**
   * Release any important references after a connection is closed (after
   *   multiple requests)
   */
  @Override
  public void cleanup()
  { 
    super.cleanup();
    _session=null;
    _cookieList.clear();
    _headers.clear();
  }

  public Principal getUserPrincipal()
  { return _userPrincipal;
  }

  public boolean isUserInRole(String role)
  { 
    //
    // XXX Must implement
    //
    return false;
  }



  @SuppressWarnings("unchecked")
  public Enumeration<String> getHeaders()
  { return new IteratorEnumeration<String>(_headerMap.keySet().iterator());
  }

  public Enumeration<Locale> getLocales()
  { return new IteratorEnumeration<Locale>(_locales.iterator());
  }

  public Locale getLocale()
  { 
    // TODO: Use Accept-Langage header to specify locales
    if (_locales.size()>0)
    { return _locales.get(0);
    }
    return Locale.getDefault();
  }


	public String getAuthType()
	{ return getHeader("Auth-Type");
	}
	
  public boolean isSecure()
  { 
    if (debugAPI)
    { log.fine(""+_secure);
    }
    return _secure;

  }

  @SuppressWarnings("unchecked")
  public Enumeration<?> getHeaders(String name)
  {
    List list=(List) _headerMap.get(name);
    if (list!=null)
    { return new IteratorEnumeration(list.iterator());
    }
    else
    { return new IteratorEnumeration(new EmptyIterator());
    }
  }

	public Cookie[] getCookies()
	{ 
    if (_cookies==null)
    { parseCookies();
    }
    return _cookies;
	}
	
	public long getDateHeader(String name)
    throws IllegalArgumentException
	{
		String value=getHeader(name);
		if (value==null)
		{ return 0;
		}
		else
		{ 
      try
      { return _rfc1123HeaderDateFormat.parse(value).getTime();
      }
      catch (ParseException x)
      { }

      for (int i=0;i<_altDateFormats.length;i++)
      {
        try
        { return _altDateFormats[i].parse(value).getTime();
        }
        catch (ParseException x)
        { }
      }
      throw new IllegalArgumentException("Unrecognized date format '"+value+"'");
		}
	}
	
	public String getHeader(String name)
	{
    Variable var=(Variable) _headerMap.getFirst(new CaseInsensitiveString(name));
    if (var!=null)
    { return var.value;
    }
    else
    { return null;
    }
	}
	
  @SuppressWarnings("unchecked")
  public Enumeration<?> getHeaderNames()
	{ 
	  ArrayList<String> names=new ArrayList<String>(_headerMap.size());
	  for (CaseInsensitiveString name 
	        : (Set<CaseInsensitiveString>) _headerMap.keySet()
	      )
	  { names.add(name.toString());
	  }
	  
    return new IteratorEnumeration<String>(names.iterator());
	}
	
	public int getIntHeader(String name)
	{
    String var=getHeader(name);
		if (var==null)
		{ return -1;
		}
		else
		{ return Integer.parseInt(var); 
		}
	}
	
	public String getMethod()
	{ return _method;
	}
	
	
	public String getRemoteUser()
	{ return _remoteUser;
	}
	
	public String getRequestedSessionId()
	{ return _requestedSessionId;
	}

	

	
	public HttpSession getSession(boolean create)
	{ 
    if (_session!=null)
    { return _session;
    }
    
    ensureQuery();
    String urlSessionId=_query.getValue("ssid");
    if (urlSessionId!=null)
    { _session=_context.getSessionManager().getSession(urlSessionId,false);
    }
    if (_session!=null)
    { 
      _requestedSessionId=urlSessionId;
      _sessionFromCookie=false;
      _sessionFromUrl=true;
      return _session;
    }
    else    
    {
      // Use session cookie, or create a new session
      _session
        =_context.getSessionManager().getSession(_requestedSessionId,create);
      if (_session!=null && _session.isNew())
      { 
        Cookie sessionCookie=new Cookie("spiralSessionId",_session.getId());
        sessionCookie.setPath("/");
        String cookieDomain=getServerName();
        if (cookieDomain.indexOf('.')<0)
        { cookieDomain=cookieDomain.concat(".local");
        }
        sessionCookie.setMaxAge(-1);
        sessionCookie.setVersion(1);
        _response.addCookie(sessionCookie);
      }
      return _session;
    }
	}
	
	public HttpSession getSession()
	{ return getSession(true);
	}


	public boolean isRequestedSessionIdFromCookie()
	{ return _sessionFromCookie;
	}

  /** 
   *@deprecated
   * @deprecated
   * @deprecated
   */
  @SuppressWarnings("deprecation")
  @Deprecated
  public boolean isRequestedSessionIdFromUrl()
	{ return _sessionFromUrl;
	}

	public boolean isRequestedSessionIdFromURL()
	{ return _sessionFromUrl;
	}

	public boolean isRequestedSessionIdValid()
	{ return _context.getSessionManager().isSessionIdValid(_requestedSessionId);
	}
	


	public ServletInputStream getInputStream()
	{ return _apiInputStream;
	}
	
	
	public String getProtocol()
	{ return _protocol;
	}
	
	public BufferedReader getReader()
	  throws IOException
	{
	  
		if (_reader==null)
		{ 
		  String charset=getCharacterEncoding();
      if (charset!=null)
      { 
        if (debugAPI)
        { log.fine("New reader has charset="+charset);
        }
          
        _reader
          =new BufferedReader
            (new InputStreamReader(_apiInputStream,charset));
      
      }
      else
      { 
        if (debugAPI)
        { log.fine("New reader has default charset");
        }
        
        _reader
          =new BufferedReader(new InputStreamReader(_apiInputStream));
      }
		}
		
		return _reader;
	}	
	
	public String getRemoteAddr()
	{ 
    if (_remoteAddr==null)
    { _remoteAddr=_remoteInetAddress.getHostAddress();
    }
    return _remoteAddr;
	}
	
	@Override
  public byte[] getRawRemoteAddress()
	{ 
    if (_rawRemoteAddr==null)
    { _rawRemoteAddr=_remoteInetAddress.getAddress();
    }
    return _rawRemoteAddr;
	}

	public String getRemoteHost()
	{ 
    if (_remoteHost==null)
    { _remoteHost=_remoteInetAddress.getHostName();
    }
    return _remoteHost;
	}
	
	
	
	public String getScheme()
	{ return _scheme;
 	}
 	
 	public String getServerName()
 	{ 
    if (_host==null)
    { parseHost();
    }
    return _host;
 	} 
 	
 	public int getServerPort()
 	{ 
    if (_host==null)
    { parseHost();
    }
    return _port;
 	}
 	
 	public String getLocalName()
 	{ return _socket.getLocalAddress().getHostName();
 	}

 	public int getLocalPort()
 	{ return _socket.getLocalPort();
 	}
 	
 	public String getLocalAddr()
 	{ return _socket.getLocalAddress().toString();
 	}
 	
 	public int getRemotePort()
 	{ return _socket.getPort();
 	}
 	
  private void parseHost()
  { 
    String fullHost=getHeader("Host");

    if (fullHost==null)
    { fullHost=_socket.getLocalAddress().getHostAddress();
    }

    int colon=fullHost.indexOf(":");
    if (colon>=0)
    {
      _host=fullHost.substring(0,colon);
      _port=Integer.parseInt(fullHost.substring(colon+1));
    }
    else
    {
      _host=fullHost;
      _port=80;
    }
  }


  /**
   * Set the response object, primarily in order
   *   that response cookies may be set when creating
   *   sessions.
   */
  public void setResponse(HttpServerResponse response)
  { _response=response;
  }

  /**
   * Return the request line as sent by the client
   */
  public String getRequestLine()
  { return _requestLine;
  }




  /**
   * <p>Read the headers, called after the request is started, from the
   *   ConnectionHandler
   * </p>
   * 
   * @throws IOException
   */
 	void readHeaders()
 		throws IOException
 	{
 	  
    if (_headersRead)
    { return;
    }
    _headersRead=true;
 		String line=null;
 		while(true)
 		{
 			line=_inputStream.readAsciiLine();
 			if (line==null || line.length()==0)
 			{ break;
 			}
 			else
 			{ parseHeader(line);
 			}
 		}
 		parseCookies();
    _inputStream.resetCount();
    int contentLength=getContentLength();
    if (contentLength>0)
    { 
      _apiInputStream
        =new CappedServletInputStream(_inputStream,contentLength);
    }
    else
    { _apiInputStream=_inputStream;
    }
    
    determineRemoteAddress();
 	}

 	/**
 	 * <p>Determine the remote IP address if there is server-side proxying
 	 *   involved
 	 * </p>
 	 * @throws IOException
 	 */
 	private void determineRemoteAddress()
 	  throws IOException
 	{
    if (_httpServer.isProxy(_socket.getInetAddress().getAddress()))
    { 
      String headerName=_httpServer.getRemoteAddressHeaderName();
      if (headerName!=null)
      {
        String headerVal=getHeader(headerName);
        if (headerVal!=null)
        { 
          if (_httpServer.getDebugService())
          { log.log(Level.DEBUG,"Proxied remote address header is "+headerVal);
          }
          // Reset our remote address to the one the proxy supplied
          this._remoteInetAddress
            =InetAddress.getByAddress(Subnet.parse(headerVal));
          this._remoteAddr=null;
          this._rawRemoteAddr=null;
          this._remoteHost=null;
        }
      }
    }
 	}
 	
 	private void parseRequest()
 	{
 		StringTokenizer tk=new StringTokenizer(_requestLine," ");
 		_method=tk.nextToken();
 		_requestURL=tk.nextToken();
    if (_requestURL.length()>8 && _requestURL.substring(0,7).equalsIgnoreCase("http://"))
    { 

      int slashPos=_requestURL.indexOf('/',7);
      Variable var=new Variable();
      var.name="Host";
      var.value=_requestURL.substring(7,slashPos);
      _headers.add(var);
      _requestURL=_requestURL.substring(slashPos);
    }
    _scheme=_secure?"https":"http";
    if (tk.hasMoreTokens())
    { _protocol=tk.nextToken();
    }
    else
    { _protocol="HTTP/0.9";
    }
    int queryPos=_requestURL.indexOf("?");
    if (queryPos>0)
    { 
      _requestURI=_requestURL.substring(0,queryPos);
      _queryString=_requestURL.substring(queryPos+1);
    }
    else
    { 
      _requestURI=_requestURL;
      _queryString=null;
    }
 	}
	
	
 	private void parseHeader(String header)
 	{
    
 		Variable var=new Variable();
 		int colonPos=header.indexOf(":");
 		var.name=header.substring(0,colonPos);
 		var.value=header.substring(colonPos+1).trim();
    if (debugProtocol)
    { _log.log(Level.DEBUG,">>> "+var.name+": "+var.value); 
    }
 		_headers.add(var);
 	}

  @SuppressWarnings("unchecked") // Not using generics here
  private void parseCookies()
  {
    // Parse each cookie header, which can contain
    //   multiple cookies.
    List cookies=(List) _headerMap.get(new CaseInsensitiveString("Cookie"));
    if (cookies!=null)
    {
      Iterator it=cookies.iterator();
      while (it.hasNext())
      { 
        final Variable var=(Variable) it.next();
        try
        { 
          if (debugProtocol)
          { _log.log(Level.DEBUG,"Reading cookie: ["+var.value+"]");
          }
          for (Cookie cookie : new CookieParser(var.value).parse())
          { 
            if (debugProtocol)
            { _log.log(Level.DEBUG,"Got cookie: ["+cookie.getName()+","+cookie.getValue()+"]");
            }
            _cookieList.add(cookie);
            if (cookie.getName().equalsIgnoreCase("spiralSessionId"))
            {
              _sessionFromCookie=true;
              _requestedSessionId=cookie.getValue();
            }              
          }
        }
        catch (ParseException x)
        { _log.log(Level.WARNING,"Parsing cookie: "+x.toString());
        }
        // parseCookie(var);
      }

      // Gather the cookies together.
      _cookies=new Cookie[_cookieList.size()];
      it=_cookieList.iterator();
      int i=0;
      while (it.hasNext())
      {	
        Cookie cookie=(Cookie) it.next();
        _cookies[i++]=cookie;
      }
    }
  }








  public void setCharacterEncoding(String encoding) 
    throws UnsupportedEncodingException
  {
    Charset.forName(encoding);
    if (debugAPI)
    { log.fine("Changed CharacterEncoding to "+encoding+" reader="+_reader);
    }
    _characterEncoding=encoding;
  }
 	
  
  
}
