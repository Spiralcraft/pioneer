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
 * Service context that provides minimal
 *   functionality, designed to be extended.
 */
package spiralcraft.pioneer.httpd;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletContext;
import javax.servlet.ServletResponse;
import javax.servlet.RequestDispatcher;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServlet;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import java.util.Enumeration;
import java.util.Set;
import java.util.Vector;
import java.util.Hashtable;

import spiralcraft.util.IteratorEnumeration;
import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.Resource;

import spiralcraft.builder.LifecycleException;
import spiralcraft.log.ClassLogger;
import spiralcraft.pioneer.io.Governer;
import spiralcraft.pioneer.io.SimpleGoverner;
import spiralcraft.pioneer.io.Filename;

import spiralcraft.text.html.URLEncoder;
import spiralcraft.time.Clock;

import spiralcraft.pioneer.util.ThrowableUtil;


import spiralcraft.pioneer.security.servlet.ServletAuthenticator;
import spiralcraft.pioneer.security.SecurityException;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import java.util.HashMap;
import java.util.Iterator;
import java.util.logging.Level;

import java.net.URL;
import java.net.MalformedURLException;


//import spiralcraft.text.html.HTEncoder;

import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.Meterable;

import spiralcraft.pioneer.net.IpFilter;

import spiralcraft.servlet.autofilter.Controller;

public class SimpleHttpServiceContext
  implements HttpServiceContext
            ,Meterable
{
  private static final ClassLogger log
    =ClassLogger.getInstance(SimpleHttpServiceContext.class);
  private static final String version="1.0pre1";
  
  //private static final String DEBUG_GROUP
  //  =SimpleHttpServiceContext.class.getName();

  private File _docRoot=new File(System.getProperty("user.dir"));
  private HttpSessionManager _sessionManager;
  private String _hostName;
  private int _port;
  private String _alias;
  private String _defaultServletName=null; // Serves the specified URI (servletPath)
  private String _rootServletName=null; // Serves the whole context (gets pathInfo)
  private HttpServiceContext _parentContext=null;
  private HashMap<String,ServletHolder> _servletMap=null;
  private HashMap<String,String> _handlerMap=null;
  private HashMap<String,String> _servletAliasMap=null;
  private String _serverInfo="Spiralcraft Web Server v"+version;
//  private URL _baseUrl;
  private Hashtable<String,Object> _attributes;
  private String _defaultUriCompletion;
	private int _requestsHandled = 0;
	private int _requestsPending = 0;
	private long _startTime;
  private ExtensionMimeTypeMap _mimeMap;
  private Resource _extensionMimeTypeMapResource;
  private Governer _governer;
  private int _maxStreamBitsPerSecond;
  private ServletAuthenticator _authenticator;
  private HashMap<String,String> _authenticatedMethods
    =new HashMap<String,String>();
  { 
    _authenticatedMethods.put("PUT","PUT");
  }
  private boolean _authenticateAllMethods=false;
  private HashMap<String,String> _initParameters
    =new HashMap<String,String>();
  
  private int _maxSessionInactiveInterval=600;

  private Meter _meter;
  private Register _requestsRegister;
	private IpFilter _allowedIpFilter;
  private IpFilter _deniedIpFilter;
  
  private Controller controller=new Controller();
  
  private WARClassLoader contextClassLoader;

  private AccessLog localAccessLog;
  protected AccessLog _accessLog=null;


  public void setAllowedIpFilter(IpFilter val)
  { _allowedIpFilter=val;
  }

  public void setDeniedIpFilter(IpFilter val)
  { _deniedIpFilter=val;
  }

  public void installMeter(Meter meter)
  {
    _meter=meter;
    _requestsRegister=_meter.createRegister(SimpleHttpServiceContext.class,"requests");
    
  }

  private Servlet _directoryRedirectServlet
    =new HttpServlet()
  {
    private static final long serialVersionUID = 1L;

    public void service(ServletRequest request,ServletResponse response)
      throws IOException,ServletException
    {
      HttpServletRequest httpRequest=(HttpServletRequest) request;
      HttpServletResponse httpResponse=(HttpServletResponse) response;
      String thisUrl=httpRequest.getRequestURL().toString();
      
      int queryPos=thisUrl.lastIndexOf("?");
      if (queryPos>=0)
      { httpResponse.sendRedirect(thisUrl.substring(0,queryPos)+"/"+thisUrl.substring(queryPos));
      }
      else
      { httpResponse.sendRedirect(thisUrl+"/");
      }
    }
  };
  
  private FilterChain _directoryRedirectFilterChain
    =new ServletHolder(_directoryRedirectServlet);
  
 
  private boolean _initialized=false;



  public void startService()
  { init();
  }

  public void stopService()
  { stop();
  }


  /**
   * Service the request
   */
  public void service(final AbstractHttpServletRequest request
                      ,final HttpServletResponse response
                      )
    throws ServletException
          ,IOException
  { 
    if (_requestsRegister!=null)
    { _requestsRegister.incrementValue();
    }
    
    try
    {
			_requestsPending++;
			_requestsHandled++;
      request.setServiceContext(this);
      request.setServletPath("");
      if (_governer!=null && (request instanceof HttpServerRequest))
      { ((HttpServerResponse) response).setGoverner(_governer);
      }
      
      if (_maxStreamBitsPerSecond>0 && (request instanceof HttpServerRequest))
      {
        SimpleGoverner governer=new SimpleGoverner();
        governer.setRateBitsPerSecond(_maxStreamBitsPerSecond);
        ((HttpServerResponse) response).setGoverner(governer);
      } 
      
      if (preFilter(request,response))
      {
        
        // Push the ClassLoader for this context
        ClassLoader lastLoader=null;
        if (contextClassLoader!=null)
        { 
          lastLoader=Thread.currentThread().getContextClassLoader();
          if (lastLoader!=contextClassLoader)
          { Thread.currentThread().setContextClassLoader(contextClassLoader);
          }
          else
          { lastLoader=null;
          }
        }
        
        // Ensure we always pop the ClassLoader if set
        try
        {
          
          FilterChain filterChain=getFilterChainForRequest(request);
          try
          {
            if (filterChain!=null)
            { 
              // This is the main act
              controller.doFilter
                ((ServletRequest) request
                ,(ServletResponse) response
                ,filterChain
                );
            }
            else
            {
              _log.log
                (Log.ERROR
                ,"No servlet configured to handle request for http://"
                  +request.getHeader("Host")
                  +request.getRequestURI()
                ); 
              response.sendError(404);
            }
          }
          catch (ServletException x)
          {
            _log.log(Log.ERROR
                    ,"ServletException handling "
                      +"http://"+request.getHeader("Host")+request.getRequestURI()
                      +": "+x.toString()
                    );
          }
          catch (IOException x)
          { throw x;
          }
          catch (Exception x)
          {
            _log.log(Log.ERROR
                    ,"Uncaught Exception handling "
                      +"http://"+request.getHeader("Host")+request.getRequestURI()
                      +": "+ThrowableUtil.getStackTrace(x)
                    );
            response.sendError(500);
          }
        }
        finally
        {
          if (lastLoader!=null)
          { Thread.currentThread().setContextClassLoader(lastLoader);
          }
        }
        
      }
    }
    catch (ServletException x)
    {
      _log.log(Log.ERROR
              ,"ServletException preprocessing request "
                +"http://"+request.getHeader("Host")+request.getRequestURI()
                +": "+x.toString()+"\r\n"+ThrowableUtil.getStackTrace(x)
              );
      response.sendError(500);
    }
    finally
    {
      if (_accessLog!=null && (request instanceof HttpServerRequest))
      { _accessLog.log((HttpServerRequest) request,(HttpServerResponse) response);
      }	
			_requestsPending--;
    }
  }

  /**
   * Perform any actions or protocol specifics
   *   before handing the request off to a servlet.
   */
  public boolean preFilter
    (AbstractHttpServletRequest request
    ,HttpServletResponse response
    )
    throws IOException
            ,ServletException
  {
    if (_parentContext!=null)
    { 
      if (!_parentContext.preFilter(request,response))
      { return false;
      }
    }
    
    if (!checkAuthentication(request,response))
    { return false;
    }
    
    return true;
  }

  /**
   * Supply the authenticator component which will authenticate
   *   requests.
   */
  public void setAuthenticator(ServletAuthenticator authenticator)
  { _authenticator=authenticator;
  }

  /**
   * Specify whether all methods will be authenticated. If this
   *   is false, only PUT will require authentication.
   */
  public void setAuthenticateAllMethods(boolean authenticateAll)
  { _authenticateAllMethods=authenticateAll;
  }

  /**
   * Return an attribute
   */
  public Object getAttribute(String name)
  { 
    if (_attributes!=null)
    { 
      Object value=_attributes.get(name);
      if (value!=null)
      { return value;
      }
    }
    
    if (_parentContext!=null)
    { return _parentContext.getAttribute(name);
    }
    else
    { return null;
    }
  }

  /**
   * Return a list of attribute names
   */
  @SuppressWarnings("unchecked") // Servlet API not generic
  public Enumeration<String> getAttributeNames()
  { 
    if (_attributes!=null)
    { return _attributes.keys();
    }
    else
    { return _parentContext.getAttributeNames();
    }
  }

  /**
   * Remove an attribute
   */
  public void removeAttribute(String name)
  { 
    if (_attributes!=null)
    { _attributes.remove(name);
    }
    else
    { _parentContext.removeAttribute(name);
    }
  }

  /**
   * Specify the value of an attribute
   */
  public void setAttribute(String name,Object value)
  { 
    if (_attributes!=null)
    { _attributes.put(name,value);
    }
    else if (_parentContext!=null)
    { _parentContext.setAttribute(name,value);
    }
    else
    { 
      _attributes=new Hashtable<String,Object>();
      _attributes.put(name,value);
    }
  }

  /**
   * Return server info
   */
  public String getServerInfo()
  { return _serverInfo;
  }

  /**
   * Return a request dispatcher for the specified uri path
   */
  public RequestDispatcher getRequestDispatcher(String uriPath)
  { 
    if (_alias!=null)
    { return _parentContext.getRequestDispatcher(uriPath);
    }
    else
    {
      return new ServerRequestDispatcher
        (this
        ,uriPath
        );
    }
  }
  
  /**
   * Return a request dispatcher for the specified uri
   */
  public RequestDispatcher getNamedDispatcher(String name)
  { 
    // XXX Implement Later
    return null;
  }

  /**
   * Return the servlet context for the specified uri
   */
  public ServletContext getContext(String uri)
  {
    // XXX Implement Later
    return null;
  }

  /**
   * Return the init parameter names
   */
  public Enumeration<String> getInitParameterNames()
  { return new IteratorEnumeration<String>(_initParameters.keySet().iterator());
  }

  /**
   * Return an init parameter
   */
  public String getInitParameter(String name)
  { return _initParameters.get(name);
  }

  /**
   * Log a message
   */
  public void log(String msg)
  { 
    if (_log.isLevel(Log.MESSAGE))
    { _log.log(Log.MESSAGE,msg);
    }
  }

  /**
   * Log an error
   *@deprecated
   */
  public void log(Exception x,String msg)
  { _log.log(Log.ERROR,ThrowableUtil.getStackTrace(x));
  }

  /**
   * Log an error
   */
  public void log(String msg,Throwable x)
  { _log.log(Log.ERROR,ThrowableUtil.getStackTrace(x));
  }

  /**
   * Specify the HttpSessionManager that will manage sessions
   *   for this context and its children
   */
  public void setSessionManager(HttpSessionManager sessionManager)
  { _sessionManager=sessionManager;
  }



  public FilterChain getFilterChain(String name)
    throws ServletException
  {
    if (_servletMap!=null)
    {
      ServletHolder holder=_servletMap.get(name);
      if (holder!=null)
      { return holder;
      }
    }
    if (_parentContext!=null)
    { return _parentContext.getFilterChain(name);
    }
    return null;
  }

  /**
   * Deprecated
   *@deprecated
   */  
  public Enumeration<Servlet> getServlets()
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return new Vector<Servlet>().elements();
  }

  /**
   * Deprecated
   *@deprecated
   */
  public Enumeration<String> getServletNames()
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return new Vector<String>().elements();
  }

  /**
   * Deprecated
   *@deprecated
   */
  public Servlet getServlet(String name)
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return null;
  }

  /**
   * Indicate support for servlet api 2.1
   */
  public int getMajorVersion()
  { return 2;
  }

  /**
   * Indicate support for servlet api 2.1
   */
  public int getMinorVersion()
  { return 1;
  }

  /**
   * Return a new URL relative to the base url for this context 
   */
  public URL getResource(String name)
    throws MalformedURLException
  { 
    if (!name.startsWith("/"))
    { throw new MalformedURLException(name+" does not start with '/'");
    }
    return _docRoot.toURI().resolve(name.substring(1)).toURL();
  }

  /**
   * Return a new InputStream for the url relative to the base
   *   url for this context
   */
  public InputStream getResourceAsStream(String name)
  { 
    try
    {
      URL resource=getResource(name);
      if (resource!=null)
      { return resource.openConnection().getInputStream();
      }
    }
    catch (Exception x)
    { throw new IllegalArgumentException(x);
    }
    return null;
  }

	/**
	 * Return the physical path that corresponds to
	 *   a server path.
	 */
	public String getRealPath(String rawUri)
  { 
    String uri=URLEncoder.decode(rawUri);
    if (uri==null)
    { 
      _log.log(Log.WARNING,"Undecodable URI "+rawUri);
      return null;
    }
    if (uri.length()>0 && uri.charAt(0)=='/')
    { uri=uri.substring(1);
    }
    if (_alias!=null)
    {
      if (!uri.startsWith(_alias))
      {
        _log.log(Log.DEBUG,uri+" does not start with "+_alias);
        return null;
      }
      else
      { 
        uri=new Filename(uri).subtract(new Filename(_alias));
        if (uri==null)
        { uri="";
        }
      }
    }

    if (isPathBounded(uri))
    { return new File(_docRoot
                    ,new Filename(uri).localize().getPath()
                    ).getAbsolutePath();
    }
    else
    { return null;
    }
  }

  /**
   * Return the session manager
   */
  public HttpSessionManager getSessionManager()
  { 
    if (_sessionManager!=null)
    { return _sessionManager;
    }
    else if (_parentContext!=null)
    { return _parentContext.getSessionManager();
    }
    else
    {
      _log.log(Log.ERROR,"No SessionManager available for root context");
      return null;
    }
  }

	/**
	 * The effective hostname of the server
	 */
	public String getName()
  { return _hostName;
  }

  public String getMimeType(String file)
  { return mapMimeType(getFileType(file));
  }

	/**
	 * The effective server port
	 */	
	public int getPort()
  { return _port;
  }

  public void setDocumentRoot(String root)
  { _docRoot=new Filename(root).localize();
  }

  public void setDefaultServletName(String servletName)
  { _defaultServletName=servletName;
  }

  public void setRootServletName(String servletName)
  { _rootServletName=servletName;
  }

  /**
   * Checks the path to ensure that any '..' don't
   *   reference any dirs outside path.
   */
  public boolean isPathBounded(String path)
  { 
    char[] chars=path.toCharArray();  

    int balance=0;
    int i=0;
    for (;i<chars.length;i++)
    {
      if (chars[i]=='/' && i>0)
      {
        if (i>=2 && chars[i-2]=='.' && chars[i-1]=='.')
        { 
          balance--;
          if (balance<0)
          { return false;
          }
        }
        else if (i>=1 && chars[i-1]=='.')
        { // Current dir
        }
        else
        { balance++;
        }
      }
      
    }
    if (balance==0 && i>=2 && chars[i-1]=='.' && chars[i-2]=='.')
    { return false;
    }
    return true;
  }

  /**
   * Specify the alias that applies to this ServiceContext.
   * The alias will be stripped from the beginning of the URI
   *   when resolving URIs to paths. URIs that do not begin with
   *   the alias will not be translated into a real path.
   */
  public void setAlias(String alias)
  { _alias=alias;
  }

  /**
   * Obtain the alias (URL prefix) that applies to
   *   this service context.
   */
  public String getAlias()
  { return _alias;
  }

  /**
   * Specify the parent context
   */
  public void setParentContext(HttpServiceContext parentContext)
  { _parentContext=parentContext;
  }

  public void setServletHolders(ServletHolder[] servletHolders)
  {
    _servletMap=new HashMap<String,ServletHolder>();
    for (ServletHolder holder: servletHolders)
    { _servletMap.put(holder.getServletName(),holder);
    }
    
  }
  
  
  /**
   * Specify the servlet map, which maps names to
   *   SerlvetHolders.
   *   
   *@deprecated Use setServletHolders()
   */
  public void setServletMap(HashMap<String,ServletHolder> servletMap)
  {
    if (_log.isLevel(Log.DEBUG))
    { _log.log(Log.DEBUG,servletMap.toString());
    }

    _servletMap=servletMap;
  }

  /**
   * Specify how request URLs map to Servlets 
   * 
   * @param servletMappings
   */
  public void setServletMappings(ServletMapping[] servletMappings)
  {
    _servletAliasMap=new HashMap<String,String>();
    _handlerMap=new HashMap<String,String>();
    for (ServletMapping mapping: servletMappings)
    { 
      if (mapping.getURLPattern().startsWith("*."))
      { 
        _handlerMap.put
          (mapping.getURLPattern().substring(2)
          ,mapping.getServletName()
          );
      }
      else if (mapping.getURLPattern().equals("/"))
      { _defaultServletName=mapping.getServletName();
      }
      else if (mapping.getURLPattern().startsWith("/")
                && mapping.getURLPattern().endsWith("/*")
                )
      { 
        // XXX Doesn't conform to spec yet
        _servletAliasMap.put
          (mapping.getURLPattern().substring
            (1,mapping.getURLPattern().length()-2)
          ,mapping.getServletName()
          );
      }
      else
      { 
        // XXX Doesn't conform to spec yet
        String name=mapping.getServletName();
        if (name.startsWith("/"))
        { name=name.substring(1);
        }
        _servletAliasMap.put
          (mapping.getURLPattern(),name);
      }
    }
    
    
  }

  /**
   * Specify the handler map, which maps request
   *   'types' to servlet names.
   *@deprecated Use setServletMappings
   */
  public void setServletAliasMap(HashMap<String,String> aliasMap)
  { 
    _servletAliasMap=aliasMap;
  }

  /**
   * Specify the handler map, which maps request
   *   'types' to servlet names.
   */
  public void setTypeHandlerMap(HashMap<String,String> handlerMap)
  {
    if (_log.isLevel(Log.DEBUG))
    { _log.log(Log.DEBUG,handlerMap.toString());
    }

    _handlerMap=handlerMap;
  }

  /**
   * Specify the name that will be appended to URIs ending
   *   in '/'
   */
  public void setDefaultURICompletion(String completion)
  { _defaultUriCompletion=completion;
  }

  public void setAccessLog(AccessLog log)
  { 
    localAccessLog=log;
    _accessLog=log;
  }

  public AccessLog getAccessLog()
  { return _accessLog;
  }

	public int getNumRequestsHandled()
	{ return _requestsHandled;
	}

	public int getNumRequestsPending()
	{ return _requestsPending;
	}

	public long getUptime()
	{ return Clock.instance().approxTimeMillis() - _startTime;
	}

  public void setExtensionMimeTypeMapResource(Resource resource)
  { _extensionMimeTypeMapResource=resource;
  }

  public void setOutputGoverner(Governer governer)
  { _governer=governer;
  }

  public void setMaxStreamBitsPerSecond(int bps)
  { 
    _maxStreamBitsPerSecond=bps;
  }

  public void setMaxSessionInactiveInterval(int secs)
  { _maxSessionInactiveInterval=secs;
  }

  //////////////////////////////////////////////////////////////////
  //
  // Protected Instance Members
  //
  //////////////////////////////////////////////////////////////////

  protected Log _log=LogManager.getGlobalLog();


  /**
   * Prepare for request handling
   */
  protected void init()
  { 
    if (_initialized)
    { throw new RuntimeException("Servlet context already initialized");
    }
    _initialized=true;

    if (_servletMap!=null)
    {
    
      Iterator<ServletHolder>it=_servletMap.values().iterator();
      while (it.hasNext())
      {
        ServletHolder holder=it.next();
        holder.setServiceContext(this);
      }
    }

//    try
//    { _baseUrl=new URL("http",_hostName,_port,"/");
//    }
//   catch (MalformedURLException x)
//    { 
//      _log.log(Log.ERROR,x.toString());
//     throw new RuntimeException(x.toString());
//    }
    
    if (_parentContext==null)
    {
      if (_sessionManager==null)
      {
        SimpleHttpSessionManager sessionManager=new SimpleHttpSessionManager();
        sessionManager.setMaxInactiveInterval(_maxSessionInactiveInterval);
        if (_meter!=null)
        { sessionManager.installMeter(_meter.createChildMeter("sessions"));
        }
        sessionManager.init();
        _sessionManager=sessionManager;
      }
      if (_mimeMap==null)
      { 
        try
        { _mimeMap=new ExtensionMimeTypeMap("ExtensionMimeTypeMap.properties");
        }
        catch (IOException x)
        { _log.log(Log.WARNING,"Error loading default mime type map: "+x.toString());
        }
        try
        {
          if (_extensionMimeTypeMapResource!=null)
          {
            if (_mimeMap==null)
            { _mimeMap=new ExtensionMimeTypeMap();
            }
            _mimeMap.addFromResource(_extensionMimeTypeMapResource);
          }
        }
        catch (IOException x)
        { _log.log(Log.WARNING,"Error loading configured mime type map: "+x.toString());
        }
      }
    }
    if (_attributes==null)
    { 
      if (_parentContext==null)
      { 
        _log.log
          (Log.MESSAGE
          ,getClass().getName()
            +" serving "+_docRoot.getPath()+" is attributes root"
          );
        _attributes=new Hashtable<String,Object>();
      }
    }
    if (_alias==null)
    { _log.log(Log.MESSAGE,"Serving path '"+_docRoot.getPath()+"' for alias '/'");
    }
    else
    { _log.log(Log.MESSAGE,"Serving path '"+_docRoot.getPath()+"' for alias '"+_alias+"'");
    }
    if (_servletAliasMap!=null 
        && _log.isDebugEnabled(HttpServer.DEBUG_SERVICE)
        )
    { _log.log(Log.DEBUG,"Servlet alias map: "+_servletAliasMap.toString());
    }

    loadWAR();
    
    // Push the ClassLoader for this context
    ClassLoader lastLoader=null;
    if (contextClassLoader!=null)
    { 
      lastLoader=Thread.currentThread().getContextClassLoader();
      if (lastLoader!=contextClassLoader)
      { Thread.currentThread().setContextClassLoader(contextClassLoader);
      }
      else
      { lastLoader=null;
      }
    }
        
    // Ensure we always pop the ClassLoader if set
    try
    {    
      startServlets();
      startFilters();
    }
    finally
    {
      if (lastLoader!=null)
      { Thread.currentThread().setContextClassLoader(lastLoader);
      }
    }

    if (localAccessLog!=null)
    { 
      try
      { localAccessLog.start();
      }
      catch (LifecycleException x)
      { throw new RuntimeException("Error starting access log",x);
      }
    }
    if (_accessLog==null && _parentContext!=null)
    { _accessLog=_parentContext.getAccessLog();
    }
      
		_startTime = Clock.instance().approxTimeMillis();
  }

  protected void stop()
  { 
    if (localAccessLog!=null)
    { 
      try
      { localAccessLog.stop();
      }
      catch (LifecycleException x)
      { log.log(Level.SEVERE,"Error starting access log",x);
      }

    }
    
    if (_sessionManager!=null && (_sessionManager instanceof SimpleHttpSessionManager))
    { ((SimpleHttpSessionManager) _sessionManager).stop();
    }
    
    if (contextClassLoader!=null)
    { 
      try
      { contextClassLoader.stop();
      }
      catch (LifecycleException x)
      { 
        _log.log(Log.WARNING,"Error stopping WAR ClassLoader: "+x.toString());
        x.printStackTrace();
      }
      contextClassLoader=null;
    }
    
  }

  /**
   * Indicate whether the specified translated path is a directory
   */
  protected boolean isDirectory(String realPath)
  { 
    if (realPath==null)
    { return false;
    }
    else
    { return new File(realPath).isDirectory();
    }
  }


  
  protected FilterChain getFilterChainForRequest(AbstractHttpServletRequest request)
    throws ServletException
  { 
    FilterChain filterChain=null;
    // String queryString=request.getQueryString();
    String originalUri=request.getRequestURI();


    // Determine whether a Servlet is mapped to the part of the 
    //   request path immediately after the context alias, if any.
    //
    // All requests paths 'contained' within this path will be handled
    //   by the Servlet.
    //
    String servletAlias=null;
    String servletPath=null;
    String rootServletPath=null;
    if (request.getAlias()!=null)
    {
      // +1 is for first  "/", which is removed for the alias
      servletAlias
        =new Filename
          (request.getPathInfo().substring(request.getAlias().length()+1))
          .getFirstName();
      if (servletAlias!=null)
      {
        servletPath
          =request.getPathInfo().substring
            (0,request.getAlias().length()+1+servletAlias.length()+1
            );
        rootServletPath
          =request.getPathInfo().substring
            (0,request.getAlias().length()+1
            );
      }
    }
    else
    { 
      servletAlias
        =new Filename(request.getPathInfo()).getFirstName();
      if (servletAlias!=null)
      {
        if (request.getPathInfo().length()>servletAlias.length())
        {
          // There is some sort of path following the servlet path
          servletPath
            =request.getPathInfo().substring
              (0,servletAlias.length()+1);
        }
        else
        { 
          servletPath=request.getPathInfo();
          rootServletPath="/";
        }
      }
    }
    if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
    { _log.log(Log.DEBUG,"Checking servlet map for '"+servletAlias+"'");
    }
    String servletName=getServletNameForAlias(servletAlias);
    if (servletName==null)
    { 
      servletPath=rootServletPath;
      servletName=_rootServletName;
    }
    
    if (servletName!=null)
    {
      if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
      { _log.log(Log.DEBUG,"Mapped to servlet named '"+servletName+"'");
      }
      filterChain=getFilterChain(servletName);
      if (filterChain==null)
      { throw new ServletException("Servlet '"+servletName+"' not found.");
      }
      request.setServletPath(servletPath);
      
      if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
      { 
        _log.log(Log.DEBUG,"servletPath="+request.getServletPath()
                  +" pathInfo="+request.getPathInfo()
                  );
      }
      return filterChain;
    }

    
    // No explicit servlet mapping, so resolve using file system and
    //   default servlet. This sets the servlet path to the whole URI,
    //   as a default whether or not we find a preset servlet.
    request.setServletPath(request.getRequestURI());

    String realPath=getRealPath(request.getRequestURI());
    
    if (realPath!=null && isDirectory(realPath))
    { 
            
      String uri=null;
      if (uri==null && _defaultUriCompletion!=null)
      { 
        uri=checkFilesystemCompletion
          (request.getServletPath(),_defaultUriCompletion);
      }
      if (uri==null)
      { uri=checkFilesystemCompletion(request.getServletPath(),"index.html");
      }
      if (uri==null)
      { uri=checkFilesystemCompletion(request.getServletPath(),"index.htm");
      }
      if (uri!=null)
      { 
        // Use default uri completion.
        request.setURI(uri.toString());
        request.setServletPath(uri.toString());
      }
      else
      { 
        // No default completion exists
        request.setURI(originalUri);
      }
      
      
    }
    else
    { request.setURI(originalUri);
    }
    
    realPath=getRealPath(request.getRequestURI());

    if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
    { _log.log(Log.DEBUG,"Locating interpreter for real path "+realPath);
    }
    
    if (realPath!=null)
    {
      String fileType=getFileType(realPath);
      if (fileType==null)
      {
        if (!originalUri.endsWith("/") && isDirectory(realPath))
        { 
          // Force a redirect to directory
          return _directoryRedirectFilterChain;
        }
      }
      servletName=getServletNameForRequestType(fileType);
      if (servletName!=null)
      { 
        filterChain=getFilterChain(servletName);
        
        if (filterChain==null)
        { throw new ServletException("Servlet '"+servletName+"' not found.");
        }

        if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
        { _log.log(Log.DEBUG,"Using servlet '"+servletName+"' for servletPath "
                    +request.getServletPath()+", file "+getRealPath(request.getServletPath())
                    );
        }
        
       
      }
    }
    return filterChain;
  }

  private String checkFilesystemCompletion(String servletPath,String completion)
  {
    StringBuilder urib=new StringBuilder(64);
    urib.append(servletPath)
      .append(servletPath.endsWith("/")?"":"/")
      .append(completion);
    String uri=urib.toString();
    
    
    String realPath=getRealPath(uri);

    if (_log.isDebugEnabled(HttpServer.DEBUG_SERVICE))
    { _log.log(Log.DEBUG,"Checking completion '"+realPath+"' for uri "
                +uri
                );
    }
    if (realPath!=null && new File(getRealPath(uri)).exists())
    { return uri;
    }
    return null;
    
  }

  //////////////////////////////////////////////////////////////////
  //
  // Private Static Members
  //
  //////////////////////////////////////////////////////////////////

  private boolean ipAllowed(byte[] addr)
  {
    if (_deniedIpFilter!=null && _deniedIpFilter.contains(addr))
    { return false;
    }

    if (_allowedIpFilter!=null)
    { return _allowedIpFilter.contains(addr);
    }

    return true;

  }

  /**
   * Ensure that the request is authenticated if required.
   */
  private boolean checkAuthentication
    (AbstractHttpServletRequest request
    ,HttpServletResponse response
    )
    throws IOException
  {
    if (!ipAllowed(request.getRawRemoteAddress()))
    { 
      response.sendError
        (403
        ,"Access not permitted from your Internet address ["+request.getRemoteAddr()+"]"
        );
      return false;
    }

    if (_authenticateAllMethods
        || _authenticatedMethods.get(request.getMethod())!=null
       )
    {
      if (_authenticator==null)
      { return false;
      }
      
      try
      {
        if (!_authenticator.isAuthenticated(request) 
            && !_authenticator.authenticate(request,response,new HashMap<String,String>())
            )
        { return false;
        }
      }
      catch (SecurityException x)
      { 
        _log.log(Log.ERROR
                ,"SecurityException processing request "
                  +"http://"+request.getHeader("Host")+request.getRequestURI()
                  +": "+x.toString()
                );
        response.sendError(500);
        return false;
      }
    }
    return true;    
  }


  //////////////////////////////////////////////////////////////////
  //
  // Protected Instance Members
  //
  //////////////////////////////////////////////////////////////////


  /**
   * Return the file 'type' that can be mapped to
   *   a mime type or a handler.
   *
   *@return The file type or null if it can't be
   *          determined.
   */
  protected String getFileType(String name)
  {
    int dotpos=name.lastIndexOf('.');
    int seppos=name.lastIndexOf(File.separatorChar);
    if (dotpos>-1 && dotpos>seppos && dotpos<name.length()-1)
    { return name.substring(dotpos+1).toLowerCase();
    }
    return null;

  }

  //////////////////////////////////////////////////////////////////
  //
  // Private Methods
  //
  //////////////////////////////////////////////////////////////////


  public String mapMimeType(String filetype)
  { 
    String mimeType=null;
    if (filetype!=null)
    { 
      if (_mimeMap!=null)
      { mimeType= (String) _mimeMap.get(filetype);
      }
      
      if (mimeType==null && _parentContext!=null)
      { mimeType=_parentContext.mapMimeType(filetype);
      }
    }

    return mimeType;
  }

  public String getServletNameForAlias(String alias)
  {
    String servletName=null;
    if (alias!=null)
    {
      if (_servletAliasMap!=null)
      { servletName=(String) _servletAliasMap.get(alias);
      }
      if (servletName==null
          && _parentContext!=null
          )
      { servletName=_parentContext.getServletNameForAlias(alias);
      }
    }
    return servletName;
  }

  public String getServletNameForRequestType(String type)
  { 
    
    String servletName=null;
    if (type!=null)
    {
      if (_handlerMap!=null)
      { 
        servletName=(String) _handlerMap.get(type);
        if (servletName!=null
            && _log.isDebugEnabled(HttpServer.DEBUG_SERVICE)
            )
        { 
          _log.log
            (Log.DEBUG
            ,"Request mapped to servlet "+servletName
            );
        }
          
      }
      if (servletName==null
          && _parentContext!=null
          && _defaultServletName==null
          && _rootServletName==null
          )
      { servletName=_parentContext.getServletNameForRequestType(type);
      }
    }
    if (servletName==null)
    { 
      if (_defaultServletName!=null
          && _log.isDebugEnabled(HttpServer.DEBUG_SERVICE)
          )
      { 
        _log.log
          (Log.DEBUG
          ,"Request mapped to default servlet "
          +_defaultServletName
          );
      }
      return _defaultServletName;
    }
    else
    { return servletName;
    }
    
  }

  private void startServlets()
  { 
    if (_servletMap!=null)
    {
      Iterator<ServletHolder> it=_servletMap.values().iterator();
      while (it.hasNext())
      {
        ServletHolder holder=it.next();
        holder.init();
      }
    }  
  }

  private void startFilters()
  {
    FilterConfig config=new FilterConfig()
    {

      public String getFilterName()
      { return "Controller";
      }

      public String getInitParameter(String arg0)
      { return null;
      }

      public Enumeration<String> getInitParameterNames()
      { return null;
      }

      public ServletContext getServletContext()
      { return SimpleHttpServiceContext.this;
      }
    };
    
    try
    { controller.init(config);
    }
    catch (ServletException x)
    { 
      // XXX Do something more secure
      x.printStackTrace();
    }
  }
  
  private void loadWAR()
  {
    try
    {
      Resource docRoot=Resolver.getInstance().resolve(_docRoot.toURI());
      Resource warRoot=docRoot.asContainer().getChild("WEB-INF");
      if (warRoot.exists())
      { 
        contextClassLoader=new WARClassLoader(warRoot);
        contextClassLoader.start();
      }
    }
    catch (IOException x)
    { _log.log(Log.ERROR,"Error loading WAR ClassLoader: "+x.toString());
    }
    catch (LifecycleException x)
    { _log.log(Log.ERROR,"Error loading WAR ClassLoader: "+x.toString());
    }
  }
  
  public Set<String> getResourcePaths(String arg0)
  {
    // TODO Auto-generated method stub
    _log.log(Log.ERROR,"getResourcePaths() not implemented");
    return null;
  }

  public String getServletContextName()
  {
    // TODO Auto-generated method stub
    _log.log(Log.ERROR,"getServletContextName() not implemented");
    return null;
  }


}
