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

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.Cookie;
import javax.servlet.ServletOutputStream;

import java.net.Socket;
import java.nio.charset.Charset;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.io.OutputStream;

import java.util.ArrayList;
import java.util.HashMap;

import spiralcraft.net.mime.ContentTypeHeader;
import spiralcraft.pioneer.util.MappedList;
import spiralcraft.pioneer.util.ListMap;
import spiralcraft.pioneer.util.Translator;
import spiralcraft.pioneer.net.ServerSocketFactory;

import spiralcraft.time.Clock;
import spiralcraft.time.ClockFormat;
import spiralcraft.util.string.StringUtil;


import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.util.TimeZone;
import java.util.Locale;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import spiralcraft.pioneer.io.Governer;

/**
 * Implementation of HttpServletResponse
 */
public class HttpServerResponse
  implements HttpServletResponse
{
  
  private static final ClassLog _log=ClassLog.getInstance(HttpServerResponse.class);
  
  
  public final static String HDR_AUTHORIZATION = "Authorization"; 
  public final static String HDR_CONNECTION = "Connection";
  public final static String HDR_CONTENT_LENGTH = "Content-Length";
  public final static String HDR_CONTENT_TYPE = "Content-Type";
  public final static String HDR_CONTENT_ENCODING = "Content-Encoding";
  public final static String HDR_COOKIE = "Cookie";
  public final static String HDR_DATE = "Date"; 
  public final static String HDR_HOST = "Host";
  public final static String HDR_IF_MODIFIED_SINCE="If-Modified-Since";
  public final static String HDR_IF_UNMODIFIED_SINCE="If-Unmodified-Since";
  public final static String HDR_LOCATION = "Location";
  public final static String HDR_LAST_MODIFIED = "Last-Modified";
  public final static String HDR_REFERER="Referer";
  public final static String HDR_SET_COOKIE = "Set-Cookie";
  public final static String HDR_SERVER = "Server";
  public final static String HDR_TRANSFER_ENCODING ="Transfer-Encoding";  
  public final static String HDR_USER_AGENT="User-Agent";
  public final static String HDR_WWW_AUTHENTICATE = "WWW-Authenticate"; 
  public final static String HDR_CACHE_CONTROL="Cache-Control";
  public final static String HDR_EXPIRES="Expires";
  public final static String HDR_RANGE="Range";
  public final static String HDR_CONTENT_RANGE="Content-Range";

  public final static String TYPE_WWW_FORM_URL_ENCODED = "application/x-www-form-urlencoded";
  public final static String ENCODING_CHUNKED = "chunked"; 
  public final static String CONNECTION_CLOSE = "close";
  public final static String CONNECTION_KEEP_ALIVE = "Keep-Alive";
  public final static String PROTOCOL_HTTP_1_1 = "HTTP/1.1";

  public final static byte[] COLON=":".getBytes();
  public final static byte[] SPACE=" ".getBytes();
  public final static byte[] EOL="\r\n".getBytes();
  public final static byte[] EQUALS="=".getBytes();
  public final static byte[] SEMICOLON=";".getBytes();

  public final static byte[] COOKIE_COMMENT="Comment".getBytes();
  public final static byte[] COOKIE_DOMAIN="domain".getBytes();
  public final static byte[] COOKIE_MAXAGE="Max-Age".getBytes();
  public final static byte[] COOKIE_EXPIRES="expires".getBytes();
  public final static byte[] COOKIE_PATH="path".getBytes();
  public final static byte[] COOKIE_SECURE="secure".getBytes();
  public final static byte[] COOKIE_VERSION="version".getBytes();


  private static final Charset UTF_8=Charset.forName("UTF-8");
  
  public enum CloseReason 
  { 
    CLIENT, 
    APPLICATION, 
    PROTOCOL, 
    STATUS, 
    LIMIT, 
    PRESSURE 
  }
  
  private Socket _socket;
  private HttpServerRequest _request;
  private ArrayList<Cookie> _cookies;
  private String _version;
  private int _status;
  private String _reason;
  private boolean _sentHeaders=false;
  private ServerOutputStream _outputStream;
  private boolean _shouldClose=false;
  private boolean _chunkStream=false;
  private int _keepaliveSeconds=10;
  private Locale _locale;
  private PrintWriter _writer;
  private boolean debugProtocol;
  private boolean debugAPI;
  private String contentType;
  private String characterEncoding="UTF-8";
  private DebugSettings debugSettings;
  private int _defaultKeepaliveSeconds=10;
  private int _newSessionKeepaliveSeconds=2;
  
  private CloseReason _closeReason;  

  private final MappedList _headers=new MappedList(new ArrayList<Variable>());
  private final ListMap _headerMap
    =_headers.addMapView
      ("name"
      ,new HashMap<String,Variable>()
      ,new Translator()
        {
          @Override
          public Object translate(Object value)
          { return ((Variable) value).name;
          }
        }
      );
  { _headerMap.setUnique(true);
  }
  
  public HttpServerResponse(HttpServerRequest request,int bufferCapacity)
  {
    _request=request;
    _outputStream=new ServerOutputStream(this,bufferCapacity);
  }

  public void setHttpServer(HttpServer server)
  { _outputStream.setHttpServer(server);
  }

  public void setDebugSettings(DebugSettings debugSettings)
  {
    this.debugSettings=debugSettings;
    this.debugProtocol=debugSettings.debugProtocol;
    this.debugAPI=debugSettings.debugAPI;
    _outputStream.setDebugSettings(debugSettings);
  }
  
  public void setTraceStream(OutputStream traceStream)
  { _outputStream.setTraceStream(traceStream);
  }

  
  public void recycle()
  {
    resetResponseState();

    _version=null;
    _sentHeaders=false;
    _writer=null;
  }

  @Override
  public void reset()
  {
    if (debugAPI)
    { _log.fine("Resetting");
    }
    resetBuffer();
    resetResponseState();    
    
  }
  
  /** Request-scoped response state, restored to defaults. */
  private void resetResponseState()
  {
    if (_cookies!=null)
    { _cookies.clear();
    }
    _status=200;
    _reason=null;
    _headers.clear();
    contentType=null;
    characterEncoding="UTF-8";
    _locale=null;
    _shouldClose=false;
    _chunkStream=false;
    _keepaliveSeconds=_defaultKeepaliveSeconds;
    _closeReason=null;
  }  
    
  /** Keep-alive idle time applied when no rule lowers it. Set by HttpServer. */
  public void setDefaultKeepaliveSeconds(int seconds)
  { _defaultKeepaliveSeconds=seconds;
  }

  /**
   * Lower the keep-alive idle time for this response; never raises it.
   * Zero or less requests a close. Applications call this (e.g. the session
   * manager on session creation) before headers are sent.
   */
  public void limitKeepalive(int seconds)
  {
    if (seconds<_keepaliveSeconds)
    { _keepaliveSeconds=seconds;
    }
  }

  public CloseReason getCloseReason()
  { return _closeReason;
  }
    
  /**
   * Called by the ConnectionHandler to reset this response at the beginning
   *   of a request
   * 
   * @param socket
   * @throws IOException
   */
  void start(Socket socket,ServerSocketFactory factory)
    throws IOException
  {
    this._socket=socket;
    this._outputStream.start(_socket,factory);
    recycle();
    debugProtocol=debugSettings.getDebugProtocol();
    debugAPI=debugSettings.getDebugAPI();
  }

  /**
   * Called at the end of a connection, which may involve multiple requests
   */
  public void cleanup()
  { 
    if (_outputStream!=null)
    { _outputStream.cleanup();
    }
     
  }

  @Override
  public int getBufferSize()
  { 
    if (debugAPI)
    { _log.fine("Buffer size is "+_outputStream.getBufferSize());
    }
    return _outputStream.getBufferSize();
  }

  @Override
  public void flushBuffer()
    throws IOException
  { 
    if (debugAPI)
    { _log.fine("Flushing buffer: "+(_writer!=null?"writer+stream":"stream"));
    }
    
    if (_writer!=null)
    { _writer.flush();
    }
    _outputStream.flush();
  }

  @Override
  public void setBufferSize(int bufferSize)
  {
    if (debugAPI)
    { _log.fine("Buffer size is "+bufferSize);
    }
    _outputStream.setBufferSize(bufferSize);
  }

  @Override
  public boolean isCommitted()
  { 
    if (debugAPI)
    { _log.fine(_outputStream.isCommitted()?"COMMITTED":"not committed");
    }
    return _outputStream.isCommitted();
  }

  public void setGoverner(Governer governer)
  { _outputStream.setGoverner(governer);
  }

  @Override
  public void addCookie(Cookie cookie)
  {
    if (debugAPI)
    { _log.fine(""+cookie);
    }

    if (cookie==null)
    { throw new IllegalArgumentException("Cookie cannot be null");
    }
    
    if (_cookies==null)
    { _cookies=new ArrayList<Cookie>();
    }
    _cookies.add(cookie);
  }

  @Override
  public boolean containsHeader(String name)
  {     
    if (debugAPI)
    { _log.fine(name+" "+_headerMap.get(name));
    }
    return _headerMap.get(name)!=null;
  }

  @Override
  @Deprecated
  public String encodeRedirectUrl(String url)
  { 
    if (debugAPI)
    { _log.fine(url);
    }
    return url;
  }
  
  @Override
  @Deprecated
  public String encodeUrl(String url)
  { 
    if (debugAPI)
    { _log.fine(url);
    }
    return url;
  }

  @Override
  public String encodeRedirectURL(String url)
  { 
    if (debugAPI)
    { _log.fine(url);
    }
    return url;
  }
  
  @Override
  public String encodeURL(String url)
  { 
    if (debugAPI)
    { _log.fine(url);
    }
    return url;
  }

  @Override
  public void sendError(int code,String msg) 
  	throws IOException
  {
    if (debugAPI)
    { _log.fine(code+" "+msg);
    }
    
    if (_request._context!=null)
    { _request._context.handleError(_request,this,code,msg,null);
    }
    else
    {
      setStatus(code);
      flushBuffer();
    }
  }
      
  @Override
  public void sendError(int code) 
    throws IOException
  {
    if (debugAPI)
    { _log.fine(""+code);
    }

	  String msg =  _statusMap.get(code);
	  if (msg==null)
    { sendError(code,"Unknown Error");
    }
	  else
    { sendError(code,msg);
    }
  }

  @Override
  public void sendRedirect(String location)
    throws IOException
  {
    if (debugAPI)
    { _log.fine(location);
    }

    setHeader(HDR_LOCATION,location);
    setStatus(SC_MOVED_TEMPORARILY);
    sendHeaders();
    if (_writer!=null)
    { _writer.flush();
    }
    _outputStream.flush();
  }

  @Override
  public void setLocale(Locale locale)
  { 
    if (debugAPI)
    { _log.fine(""+locale);
    }
    _locale=locale;     
  }

  @Override
  public Locale getLocale()
  { 
    if (_locale==null)
    { _locale=Locale.getDefault();
    }
    if (debugAPI)
    { _log.fine(""+_locale);
    }
    return _locale;
  }

  @Override
  public void setStatus(int code)
  { 
    if (debugAPI)
    { _log.fine(""+code);
    }
    _status=code;
    _reason=_statusMap.get(code);
  }

  @Override
  @Deprecated
  public void setStatus(int code,String message)
  {
    if (debugAPI)
    { _log.fine(code+" "+message);
    }
    _status=code;
    _reason=message;
  }

  /**
   * Return the status code
   */
  public int getStatus()
  { 
    if (debugAPI)
    { _log.fine(""+_status);
    }
    return _status;
  }

  @Override
  public void setIntHeader(String name, int value)
  {
    _headerMap.remove(name);
    addIntHeader(name,value);
  }

  @Override
  public void setDateHeader(String name, long date)
  {
    _headerMap.remove(name);
    addDateHeader(name,date);
  }
  
  @Override
  public void addIntHeader(String name, int value)
  { addHeader(name, Integer.toString(value));
  }

  @Override
  public void addDateHeader(String name,long date)
  { addHeader(name, _headerDateFormat.format(new Date(date)));
  }

  @Override
  public void addHeader(String name, String value)
  { 
    if (debugAPI)
    { _log.fine(name+" = "+value);
    }
    _headers.add(new Variable(name,value));
  }

  @Override
  public void setHeader(String name, String value)
  {
    _headerMap.remove(name);
    addHeader(name,value);
  }

  public String getHeader(String name)
  { 
    Variable var=(Variable) _headerMap.getFirst(name);
    if (debugAPI)
    { _log.fine(name+" = "+(var!=null?var.value:"null"));
    }
    
    return var!=null?var.value:null;
  }

  @Override
  public void setContentType(String value)
  { 
    if (debugAPI)
    { _log.fine(value);
    }
    
    
    try
    {
      String contentType;
      String characterEncoding;
      
      ContentTypeHeader header=new ContentTypeHeader("Content-Type",value);
      characterEncoding=header.getParameter("charset");
      contentType=header.getFullType();
      this.contentType=contentType;
      if (characterEncoding!=null)
      { this.characterEncoding=characterEncoding;
      }
      
    }
    catch (IOException x)
    { _log.log(Level.WARNING,"Bad content type "+value,x);
    }
    
    setHeader
      (HDR_CONTENT_TYPE
      ,contentType
      +(characterEncoding!=null?";charset="+this.characterEncoding:"")
      );
  }
  
  @Override
  public String getContentType()
  { 
    if (debugAPI)
    { _log.fine(getHeader(HDR_CONTENT_TYPE));
    }
    return getHeader(HDR_CONTENT_TYPE);
  }
  
  @Override
  public void setCharacterEncoding(String value)
  {
    if (debugAPI)
    { _log.fine(value);
    }
    characterEncoding=value;
    if (contentType!=null)
    {
      contentType=StringUtil.discardAfter(contentType,';');

      setHeader
        (HDR_CONTENT_TYPE
        ,contentType
        +(characterEncoding!=null?";charset="+characterEncoding:"")
        );
    }
  }
  
  @Override
  public void setContentLength(int len)
  { 
    if (debugAPI)
    { _log.fine(""+len);
    }
    setHeader(HDR_CONTENT_LENGTH,Integer.toString(len));
  }

  public String getReason()
  { return _reason;
  }
  
  @Override
  public ServletOutputStream getOutputStream()
  { 
    if (debugAPI)
    { _log.fine(""+_outputStream);
    }
    return _outputStream;
  }

  @Override
  public PrintWriter getWriter()
  {
    if (_writer==null)
    { 
      Charset charset
        =characterEncoding!=null
        ?Charset.forName(characterEncoding)
        :null
        ;
        
      if (charset==null)
      { charset=UTF_8;
      }
      _writer=new PrintWriter(new OutputStreamWriter(_outputStream,charset));
    }
    if (debugAPI)
    { _log.fine(""+_writer);
    }
    return _writer;
  }

  @Override
  public String getCharacterEncoding()
  { 
    if (debugAPI)
    { _log.fine(characterEncoding);
    }
    return characterEncoding;
  }

  public boolean shouldClose()
  { return _shouldClose;
  }

  public int getKeepaliveSeconds()
  { return _keepaliveSeconds;
  }

  @SuppressWarnings("unchecked")
  public void sendHeaders()
    throws IOException
  { 
    if (!_sentHeaders)
    {
      if (debugSettings.getDebugService())
      { _log.fine("Sending headers");
      }

      defaultHeaders();

      decideConnection();
      
      // Determine chunking and keepalive status
	    if (PROTOCOL_HTTP_1_1.equals(_version))
	    {

        String encoding=getHeader(HDR_TRANSFER_ENCODING);
        String connection=getHeader(HDR_CONNECTION);
        String length=getHeader(HDR_CONTENT_LENGTH);
        String requestConnection=_request.getHeader(HDR_CONNECTION);

        if (debugProtocol)
        { 
          _log.debug("Commiting to HTTP/1.1 headers... "
                    +"encoding="+encoding
                    +", connection="+connection
                    +", length="+length
                    +", requestConnection="+requestConnection
                    +", shouldClose="+_shouldClose
                    +", keepaliveSeconds="+_keepaliveSeconds
                    +", closeReason="+_closeReason
                    );
        }
        


        if (_status!=304 && _status!=204 && !(_status>=100 && _status<=199))
        {
          if (encoding!=null && encoding.equals(ENCODING_CHUNKED))
          { _chunkStream=true;
          }
          else if (length==null)
          {
           
            // Chunk always if no content length.
            //
            // Closing the stream to complete the response 
            //   causes problems with TLS (2025-08-06)
            
            setHeader(HDR_TRANSFER_ENCODING
                     ,ENCODING_CHUNKED
                     );
            _chunkStream=true;

          }
          else 
          { _chunkStream=false;
          }
		    }
        else
        {
          // No-entity responses must not contain entity-headers.
          _chunkStream=false;
        }
        // / http1.1 section
	    }
	    else
	    { 
	      // HTTP 1.0
        _chunkStream=false;
	    }
	    
      // Sanity check
	    _outputStream.setChunking(false);
 	    _sentHeaders=true;
      if (debugProtocol)
      { _log.log(Level.DEBUG,"<<< "+_version+" "+_status+" "+_reason);
      }

	    _outputStream.write(_version);
      _outputStream.write(SPACE);
      _outputStream.write(Integer.toString(_status));
      _outputStream.write(SPACE);
      _outputStream.write(_reason!=null?_reason:"");
      _outputStream.write(EOL);

      if (_status>=400)
      { 
        setHeader(HDR_CACHE_CONTROL,"max-age=0");
        setDateHeader(HDR_EXPIRES,0);
      }
      for (Variable var : (Iterable<Variable>) _headers)
      {
        if (debugProtocol)
        { _log.log(Level.DEBUG,"<<< "+var.name+": "+var.value);
        }
        _outputStream.write(var.name);
        _outputStream.write(COLON);
        _outputStream.write(SPACE);
        _outputStream.write(var.value);
        _outputStream.write(EOL);
      }
      if (_cookies!=null)
      {
        for (Cookie cookie : _cookies)
        {

          if (debugProtocol)
          { _log.log(Level.DEBUG,"<<< Set-Cookie: "+cookie.getName()+"="+cookie.getValue());
          }
          _outputStream.write(HDR_SET_COOKIE);
          _outputStream.write(COLON);
          _outputStream.write(SPACE);
          _outputStream.write(cookie.getName());
          _outputStream.write(EQUALS);
          _outputStream.write(cookie.getValue());
          if (cookie.getComment()!=null)
          { 
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_COMMENT);
            _outputStream.write(EQUALS);
            _outputStream.write(cookie.getComment());
          }
          if (cookie.getDomain()!=null)
          { 
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_DOMAIN);
            _outputStream.write(EQUALS);
            _outputStream.write(cookie.getDomain());
          }

          if (cookie.getMaxAge()>=0)
          { 
            
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_MAXAGE);
            _outputStream.write(EQUALS);
            _outputStream.write(Integer.toString(cookie.getMaxAge()));

            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_EXPIRES);
            _outputStream.write(EQUALS);
          
            _outputStream.write
              (_headerDateFormat.format
                (new Date
                  (Clock.instance().approxTimeMillis()
                  +(1000* (long) (cookie.getMaxAge()))
                  )
                )
              );
          }

          if (cookie.getPath()!=null)
          { 
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_PATH);
            _outputStream.write(EQUALS);
            _outputStream.write(cookie.getPath());
          }
          if (cookie.getSecure())
          { 
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_SECURE);
          }
          if (cookie.getVersion()!=0)
          { 
            _outputStream.write(SEMICOLON);
            _outputStream.write(SPACE);
            _outputStream.write(COOKIE_VERSION);
            _outputStream.write(EQUALS);
            _outputStream.write(Integer.toString(cookie.getVersion()));
          }
           
          _outputStream.write(EOL);
        }
      }
      _outputStream.write(EOL);
      if (_chunkStream)
      { 
        _outputStream.flush();
        _outputStream.setChunking(true);
      }
      _outputStream.resetCount();
      
    }
	    
  }

  /**
   * Finishes the reponse by closing the socket or sending the 
   *   'end' chunk.
   */
  public void finish()
  {
    if (debugProtocol)
    { _log.fine("Flushing  outputStream");
    }

     
//     Flushing the -writer- doesn't seem to work very well, so leaving it 
//      out.
    
    if (_writer!=null)
    { _writer.flush();
    }
    
    // XXX We always need to call _outputStream.flush() because
    //   the writer doesn't always call it
    
    try
    { _outputStream.flush();
    }
    catch (IOException x)
    { 
      _log.log(Level.DEBUG
              ,"Finishing response- flushing stream "+x.toString()
              );
    }
    


    if (_shouldClose)
    {
      if (_chunkStream)
      {
        if (debugProtocol)
        {
          _log.log(Level.DEBUG
                  ,"Finishing chunked response before closing connection from "
                  +_socket.getInetAddress().getHostAddress()
                  );
        }
        try
        {
          _outputStream.finish();
        }
        catch (IOException x)
        {
          _log.log(Level.INFO
                  ,"Exception Finishing response before closing connection from "
                  +_socket.getInetAddress().getHostAddress()
                  +": "+x.toString()
                  );
        }
      }
      
      if (debugProtocol)
      {
        _log.log(Level.DEBUG
                ,"Closing connection from "
                +_socket.getInetAddress().getHostAddress()
                );
      }

      try
      { 
        // This is just informative, actual shutdown happens at
        //   a higher level
        _outputStream.close();
      }
      catch (IOException x)
      { }
    }
    else if (_chunkStream)
    { 
      if (debugProtocol)
      {
        _log.log(Level.DEBUG
                ,"Finishing response for keepalive connection from "
                +_socket.getInetAddress().getHostAddress()
                );
      }
      try
      {
        _outputStream.finish();
      }
      catch (IOException x)
      {
        _log.log(Level.INFO
                ,"Exception Finishing response for keepalive connection from "
                +_socket.getInetAddress().getHostAddress()
                +": "+x.toString()
                );
      }
    }


  }

  public int getByteCount()
  { return _outputStream.getCount();
  }

  @Override
  public void resetBuffer()
  { 
    if (debugAPI)
    { _log.fine("Resetting buffer: sentHeaders="+_sentHeaders);
    }
    if (_writer!=null)
    { _writer.flush();
    }
    if (isCommitted())
    { throw new IllegalStateException("Response already committed");
    }
    _outputStream.resetBuffer();
    _sentHeaders=false;
  }

  //////////////////////////////////////////////////////////////////
  //
  // Private Static Members
  //
  //////////////////////////////////////////////////////////////////

  private final DateFormat _headerDateFormat
    =new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
  { _headerDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
  }

  private final ClockFormat _formatTimeWatcher
    =new ClockFormat(_headerDateFormat,1000);

  /** Responses after which the connection is not worth keeping. */
  private static boolean statusClosesConnection(int status)
  {
    if (status>=500)
    { return true;
    }
    switch (status)
    {
      case 400: case 403: case 405: case 408: case 411:
      case 413: case 414: case 429: case 431:
        return true;
      default:
        return false;
    }
  }

  /** Short keep-alive for statuses browsers hit mid-page-load (favicons etc). */
  private static int statusKeepaliveLimit(int status)
  { return status==404?2:Integer.MAX_VALUE;
  }

  /** Case-insensitive match of a token in a comma-separated header value. */
  private static boolean hasToken(String headerValue,String token)
  {
    if (headerValue==null)
    { return false;
    }
    for (String part : headerValue.split(","))
    {
      if (part.trim().equalsIgnoreCase(token))
      { return true;
      }
    }
    return false;
  }

  private final static HashMap<Integer,String> _statusMap 
    = new HashMap<Integer,String>();
  static
  {
    try
	  {
	    Field[] fields
        = HttpServletResponse.class.getDeclaredFields();
	    for (int i=0;i<fields.length; i++)
	    {
        int mods = fields[i].getModifiers();
        if (Modifier.isFinal(mods)
            && Modifier.isStatic(mods)
            && fields[i].getType().equals(Integer.TYPE)
            && fields[i].getName().startsWith("SC_")
            )
        {
          _statusMap.put
            ((Integer) fields[i].get(null)
            ,fields[i]
              .getName()
              .substring(3) 
              .replace('_',' ')
            );
        }
      }              
    }
    catch (Exception x)
    { 
      _log.log(Level.WARNING,"Exception creating error map",x);
    }
  }

  static class Variable
  {
    public String name;
    public String value;

    public Variable(String name,String value)
    {
      this.name=name;
      this.value=value;
    }
    
   
  }


  private void markClose(CloseReason reason)
  {
    _shouldClose=true;
    if (_closeReason==null)
    { _closeReason=reason;
    }
  }
  
  private final void defaultHeaders()
  {
    if (_status==0)
    { _status=200;
    }
    
    if (_reason==null)
    { _reason="OK";
    }

	  if (getHeader(HDR_DATE)==null
        && (_status<100
            || _status>199
           )
        )
	  { setHeader(HDR_DATE,_formatTimeWatcher.approxTimeFormatted());
    }
    
    if (_version==null)
    { _version=_request.getProtocol();
    }

    
  }

  private void decideConnection()
  {
    boolean http11=PROTOCOL_HTTP_1_1.equals(_version);
    String requestConnection=_request.getHeader(HDR_CONNECTION);

    if (hasToken(requestConnection,CONNECTION_CLOSE))
    { markClose(CloseReason.CLIENT);
    }
    else if (hasToken(getHeader(HDR_CONNECTION),CONNECTION_CLOSE))
    { markClose(CloseReason.APPLICATION);
    }
    else if (!http11
             && (!hasToken(requestConnection,"keep-alive")
                 || (getHeader(HDR_CONTENT_LENGTH)==null && _status!=304)
                )
            )
    { markClose(CloseReason.PROTOCOL); // 1.0: opt-in only, and needs a length
    }
    else if (statusClosesConnection(_status))
    { markClose(CloseReason.STATUS);
    }
    else
    {
      limitKeepalive(statusKeepaliveLimit(_status));
      if (!http11)
      { limitKeepalive(5);
      }
      
      HttpSession session=_request.currentSession();
      if (session!=null && session.isNew())
      { limitKeepalive(_newSessionKeepaliveSeconds);
      }
            
      if (_keepaliveSeconds<=0)
      { markClose(CloseReason.LIMIT);
      }
    }

    if (_shouldClose)
    { setHeader(HDR_CONNECTION,CONNECTION_CLOSE);
    }
    else if (!http11)
    { setHeader(HDR_CONNECTION,"keep-alive");
    }
  }

}
