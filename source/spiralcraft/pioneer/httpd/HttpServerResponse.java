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

  public final static byte[] END_CHUNK="0\r\n\r\n".getBytes();

  private static final Charset UTF_8=Charset.forName("UTF-8");
  
  private Socket _socket;
  private HttpServerRequest _request;
  private ArrayList<Cookie> _cookies;
  private String _version;
  private int _status;
  private String _reason;
  private boolean _sentHeaders=false;
  private ServerOutputStream _outputStream;
  private boolean _shouldClose=true;
  private boolean _chunkStream=false;
  private int _keepaliveSeconds=30;
  private Locale _locale;
  private PrintWriter _writer;
  private boolean debugProtocol;
  private boolean debugAPI;
  private String contentType;
  private String characterEncoding="UTF-8";
  private DebugSettings debugSettings;

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
    _outputStream.setDebugSettings(debugSettings);
  }
  
  public void setTraceStream(OutputStream traceStream)
  { _outputStream.setTraceStream(traceStream);
  }

  
  public void recycle()
  {

    if (_cookies!=null)
    { _cookies.clear();
    }
    _version=null;
    _status=0;
    _reason=null;
    _sentHeaders=false;
    _shouldClose=true;
    _chunkStream=false;
    _keepaliveSeconds=30;
    _writer=null;
    contentType=null;
    characterEncoding="UTF-8";
    _headers.clear();
  }

  @Override
  public void reset()
  {
    if (debugAPI)
    { _log.fine("Resetting");
    }
    resetBuffer();
    if (_cookies!=null)
    { _cookies.clear();
    }
    _status=200;
    _reason=null;
    _sentHeaders=false;
    contentType=null;
    characterEncoding="UTF-8";
    _headers.clear();
    
  }
  
  /**
   * Called by the ConnectionHandler to reset this response at the beginning
   *   of a connection, in preparation for multiple requests
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
    { _log.fine("Flushing buffer: "+_writer!=null?"writer+stream":"writer");
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

      
      // Determine chunking and keepalive status
	    if (PROTOCOL_HTTP_1_1.equals(_version))
	    {

        String encoding=getHeader(HDR_TRANSFER_ENCODING);
        String connection=getHeader(HDR_CONNECTION);
        String length=getHeader(HDR_CONTENT_LENGTH);
        String requestConnection=_request.getHeader(HDR_CONNECTION);
        
        boolean keepAliveOverride=false;
        if (_request.isSecure())
        {
          String userAgent=_request.getHeader(HDR_USER_AGENT);
          if (userAgent!=null)
          {
            if (userAgent.indexOf("MSIE")>-1)
            { keepAliveOverride=true;
            }
          }
        }

        if (CONNECTION_CLOSE.equals(requestConnection))
        {
          // Always close if asked by client
          _shouldClose=true;
          setHeader(HDR_CONNECTION,CONNECTION_CLOSE);
        }
        else if (CONNECTION_CLOSE.equals(connection))
        {
          // Close if asked by Servlet, no need to set header.
          _shouldClose=true;
        }
        else if (keepAliveOverride)
        {
          // Close if keep-alive is overridden
          _shouldClose=true;
          setHeader(HDR_CONNECTION,CONNECTION_CLOSE);
        }
        else
        { 
          // Keep open by default
          _shouldClose=false;
        }

        if (_status!=304)
        {
          if (encoding!=null && encoding.equals(ENCODING_CHUNKED))
          { _chunkStream=true;
          }
          else if (length==null)
          {
            if (!_shouldClose)
            {
              // Chunk by default if we aren't closing
              setHeader(HDR_TRANSFER_ENCODING
                       ,ENCODING_CHUNKED
                       );
              _chunkStream=true;
            }
            else
            { 
              // Close signals end of stream, no need to chunk
              _chunkStream=false;
            }
          }
		    }
        else
        {
          // No-entity responses must not contain entity-headers.
          _chunkStream=false;
        }
	    }
	    else
	    { 
        String connection=getHeader(HDR_CONNECTION);
        String requestConnection=_request.getHeader(HDR_CONNECTION);
        String length=getHeader(HDR_CONTENT_LENGTH);
        if (CONNECTION_KEEP_ALIVE.equals(requestConnection)
            && (length!=null || _status==304)
            &&!CONNECTION_CLOSE.equals(connection)
           )
        {
          _shouldClose=false;
          _keepaliveSeconds=5;
        }
        else
        {
          setHeader(HDR_CONNECTION,CONNECTION_CLOSE);
          _shouldClose=true;
        }
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
      if (debugProtocol)
      {
        _log.log(Level.DEBUG
                ,"Closing connection from "
                +_socket.getInetAddress().getHostAddress()
                );
      }

      try
      { _socket.close();
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
        _outputStream.setChunking(false);
        _outputStream.write(END_CHUNK);
        _outputStream.flush();
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

    if (getHeader(HDR_CONNECTION)==null)
    {
      
      String connection=_request.getHeader(HDR_CONNECTION);
      if (connection!=null && connection.length()>0)
      { setHeader(HDR_CONNECTION,connection);
      }
      else
      { 
        // Don't set empty header value
        setHeader(HDR_CONNECTION,"close");
      }
      
    }
    
  }



}
