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
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletException;
import javax.servlet.ServletContext;
import javax.servlet.ServletRequestAttributeEvent;
import javax.servlet.ServletRequestAttributeListener;
import javax.servlet.ServletRequestEvent;
import javax.servlet.ServletRequestListener;
//import javax.servlet.ServletRequest;
//import javax.servlet.ServletResponse;
import javax.servlet.RequestDispatcher;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
//import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Hashtable;

import java.util.HashMap;

import java.net.URI;
import java.net.URL;
import java.net.MalformedURLException;


import spiralcraft.util.IteratorEnumeration;
import spiralcraft.util.Path;

import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.Resource;
import spiralcraft.vfs.batch.Search;

import spiralcraft.common.LifecycleException;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import spiralcraft.pioneer.io.Governer;
import spiralcraft.pioneer.io.SimpleGoverner;
import spiralcraft.text.html.URLEncoder;

import spiralcraft.time.Clock;

import spiralcraft.pioneer.util.ThrowableUtil;

import spiralcraft.pioneer.security.servlet.ServletAuthenticator;
import spiralcraft.pioneer.security.SecurityException;

import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.Meterable;

import spiralcraft.net.ip.AddressSet;

import spiralcraft.servlet.autofilter.Controller;


public class SimpleHttpServiceContext
  implements HttpServiceContext
            ,Meterable
{
  protected static final ClassLog log
    =ClassLog.getInstance(SimpleHttpServiceContext.class);
  private static final String version="1.0pre1";
  
  //private static final String DEBUG_GROUP
  //  =SimpleHttpServiceContext.class.getName();

  private File _docRootDir=new File(System.getProperty("user.dir"));
  private URI _docRootURI=_docRootDir.toURI();
  
  private HttpSessionManager _sessionManager;
  private String _hostName;
  private int _port;
  private String _contextPath="";
  private String _defaultServletName=null; // Serves the specified URI (servletPath)
  private String _rootServletName=null; // Serves the whole context (gets pathInfo)
  private HttpServiceContext _parentContext=null;
  private HashMap<String,ServletHolder> _servletMap=null;
  private HashMap<String,String> _suffixServletNameMap=null;
  private HashMap<String,String> _prefixServletNameMap=null;
  private ArrayList<FilterMapping> _filterMappings=null;
  private HashMap<String,FilterHolder> _filterMap=null;
  private ArrayList<String> _listenerClassNames;
  private ArrayList<ServletContextListener> _listeners;
  private ArrayList<ServletContextAttributeListener> _attributeListeners;
  private ArrayList<ServletRequestAttributeListener> _requestAttributeListeners;
  private ArrayList<ServletRequestListener> _requestListeners;
  private ArrayList<String> _welcomeFileList;  

  private Stack<ServletHolder> _servletStack
    =new Stack<ServletHolder>();
  private Stack<FilterHolder> _filterStack
    =new Stack<FilterHolder>();


  private String _serverInfo="Spiralcraft Web Server v"+version;
//  private URL _baseUrl;
  private Hashtable<String,Object> _attributes;
  private String _defaultUriCompletion;
	private int _requestsHandled = 0;
	private int _requestsPending = 0;
	private long _startTime;
	private volatile boolean _running;
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
	private AddressSet _allowedIpFilter;
  private AddressSet _deniedIpFilter;
  
  private Controller controller;
  
  private ClassLoader contextClassLoader;
  private boolean debugWAR;
  private boolean useURLClassLoader;

  private AccessLog localAccessLog;
  protected AccessLog _accessLog=null;
  private String _servletContextName=null;
  protected boolean debug;
  private HttpServer _server;
  protected String virtualHostName;  
 
  private boolean _initialized=false;


  /////////////////////////////////////////////////////////////////////////
  //
  // Service Methods
  //
  /////////////////////////////////////////////////////////////////////////
  
  
  /**
   * Service the request
   */
  public void service(final AbstractHttpServletRequest request
                      ,final HttpServletResponse response
                      )
    throws ServletException
          ,IOException
  { 
    
    // TODO: Unwrap this logic to use a "ServerFilter", perhaps in
    //   new httpd module.
    if (debug || request.getHttpServer().getDebugService())
    { log.fine(request.getRequestURI());
    }
    
    if (!_running)
    { 
      response.sendError(503);
      return;
    }
    
    if (_requestsRegister!=null)
    { _requestsRegister.incrementValue();
    }
    
    try
    {
			_requestsPending++;
			_requestsHandled++;
      request.setServiceContext(this);
      
      // We must to this here to compute other path fields
      request.updateContextPath(getContextPath());
      
      
      if (request instanceof HttpServerRequest)
      {
        
        if (_governer!=null)
        { ((HttpServerResponse) response).setGoverner(_governer);
        }
      
        if (_maxStreamBitsPerSecond>0)
        {
          SimpleGoverner governer=new SimpleGoverner();
          governer.setRateBitsPerSecond(_maxStreamBitsPerSecond);
          ((HttpServerResponse) response).setGoverner(governer);
        }
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
              fireRequestInitialized(request);
              // This is the main act
              filterChain.doFilter(request,response);
              fireRequestDestroyed(request);
            }
            else
            {
              log.log
                (Level.SEVERE
                ,"No servlet configured to handle request for http://"
                  +request.getHeader("Host")
                  +request.getRequestURI()
                ); 
              response.sendError(404);
            }
          }
          catch (ServletException x)
          {
            log.log(Level.SEVERE
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
            log.log(Level.SEVERE
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
        
      } // if (preFilter(request,response)
      else
      { 
        if (debug)
        { log.fine("Request "+request.getRequestURL()+" failed pre-filter");
        }
      }
    }
    catch (ServletException x)
    {
      log.log(Level.SEVERE
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
			request.setServiceContext(null);
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
    
    if (request instanceof HttpServerRequest)
    {
      // Filtering specific to end-client requests
      
      if (request.getRequestURI().startsWith("/WEB-INF"))
      { 
        response.sendError(403);
        return false;
      }
    }
            
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
   * Return an attribute
   */
  public Object getAttribute(String name)
  { 
    if (_attributes!=null)
    { 
      Object value=_attributes.get(name);
      if (debug)
      { log.fine("Attribute lookup "+name+"="+value);
      }
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

  public void setContextPath(String contextPath)
  { _contextPath=contextPath;
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
    { 
      Object oldval=_attributes.remove(name);
      if (oldval!=null)
      { fireAttributeRemoved(name,oldval);
      }
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
    
    if (debug)
    { log.fine("Setting attribute "+name+" = "+value);
    }
    if (_attributes!=null)
    { 
      Object oldval=_attributes.get(name);
      if (oldval!=null)
      {
        _attributes.put(name,value);
        fireAttributeReplaced(name,oldval);
      }
      else
      {
        _attributes.put(name,value);
        fireAttributeAdded(name,value);
      }
    }
    else
    { 
      _attributes=new Hashtable<String,Object>();
      _attributes.put(name,value);
      fireAttributeAdded(name,value);
    }
  }

  private void fireAttributeAdded(String name,Object value)
  {
    if (_attributeListeners!=null)
    { 
      ServletContextAttributeEvent event
        =new ServletContextAttributeEvent(this,name,value);
      for (ServletContextAttributeListener listener : _attributeListeners)
      { listener.attributeAdded(event);
      }
    }
    
  }

  private void fireAttributeReplaced(String name,Object value)
  {
    if (_attributeListeners!=null)
    { 
      ServletContextAttributeEvent event
        =new ServletContextAttributeEvent(this,name,value);
      for (ServletContextAttributeListener listener : _attributeListeners)
      { listener.attributeReplaced(event);
      }
    }
    
  }

  private void fireAttributeRemoved(String name,Object value)
  {
    if (_attributeListeners!=null)
    { 
      ServletContextAttributeEvent event
        =new ServletContextAttributeEvent(this,name,value);
      for (ServletContextAttributeListener listener : _attributeListeners)
      { listener.attributeRemoved(event);
      }
    }
  }

  public void fireRequestAttributeReplaced
    (HttpServletRequest request,String name,Object value)
  {
    if (_requestAttributeListeners!=null)
    { 
      ServletRequestAttributeEvent event
        =new ServletRequestAttributeEvent(this,request,name,value);
      for (ServletRequestAttributeListener listener : _requestAttributeListeners)
      { listener.attributeReplaced(event);
      }
    }
  }  
  
  public void fireRequestAttributeAdded
    (HttpServletRequest request,String name,Object value)
  {
    if (_requestAttributeListeners!=null)
    { 
      ServletRequestAttributeEvent event
        =new ServletRequestAttributeEvent(this,request,name,value);
      for (ServletRequestAttributeListener listener : _requestAttributeListeners)
      { listener.attributeAdded(event);
      }
    }
  }  

  public void fireRequestAttributeRemoved
    (HttpServletRequest request,String name,Object value)
  {
    if (_requestAttributeListeners!=null)
    { 
      ServletRequestAttributeEvent event
        =new ServletRequestAttributeEvent(this,request,name,value);
      for (ServletRequestAttributeListener listener : _requestAttributeListeners)
      { listener.attributeRemoved(event);
      }
    }
  }  
  
  private void fireRequestInitialized(HttpServletRequest request)
  {
    if (_requestListeners!=null)
    { 
      ServletRequestEvent event
        =new ServletRequestEvent(this,request);
      for (ServletRequestListener listener : _requestListeners)
      { listener.requestInitialized(event);
      }
    }
  }
  
  private void fireRequestDestroyed(HttpServletRequest request)
  {
    if (_requestListeners!=null)
    { 
      ServletRequestEvent event
        =new ServletRequestEvent(this,request);
      for (ServletRequestListener listener : _requestListeners)
      { listener.requestDestroyed(event);
      }
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
    if (debug)
    { log.fine("Getting dispatcher for "+uriPath);
    }
    
    return new ServerRequestDispatcher
      (this
      ,_contextPath+uriPath
      );
  }
  
  /**
   * Return a request dispatcher for the specified uri
   */
  public RequestDispatcher getNamedDispatcher(String name)
  { 
    if (debug)
    { log.fine("NOT IMPLEMENTED! ("+name+")");
    }
    throw new UnsupportedOperationException("getNamedDispatcher("+name+")");

  }

  /**
   * Return the servlet context for the specified uri
   */
  public ServletContext getContext(String uri)
  { 
    throw new UnsupportedOperationException("getContext("+uri+")");
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

  public void setInitParameters(InitParameter[] params)
  {
    for (InitParameter param:params)
    { setInitParameter(param.getName(),param.getValue());
    }
  }
  
  /**
   * Specify the initialization parameters as a block of text in the
   *   java.util.Properties format.
   * 
   * @param initParameterText
   */
  public void setInitParametersAsText(String initParametersText)
  {
    Properties props=new Properties();
    try
    { props.load(new StringReader(initParametersText));
    }
    catch (IOException x)
    { throw new IllegalArgumentException(x);
    }
    for (String name: props.stringPropertyNames())
    { _initParameters.put(name,props.getProperty(name));
    }
  }
  
  /**
   * Log a message
   */
  public void log(String msg)
  { 
    if (log.canLog(Level.INFO))
    { log.log(Level.INFO,msg);
    }
  }

  /**
   * Log an error
   *@deprecated
   */
  @Deprecated
  public void log(Exception x,String msg)
  { log.log(Level.SEVERE,msg,x);
  }

  /**
   * Log an error
   */
  public void log(String msg,Throwable x)
  { log.log(Level.SEVERE,msg,x);
  }


  public String getLogPrefix()
  { 
    return (virtualHostName!=null?"//"+virtualHostName:"")
       +(getContextPath().length()>0?getContextPath():"/")
       +(getServletContextName()!=null?" \""+getServletContextName()+"\" ":"");
  }


  public FilterChain getServletFilterChain(String servletName)
    throws ServletException
  {
    
    if (_servletMap!=null)
    {
      ServletHolder holder=_servletMap.get(servletName);
      
      if (holder!=null)
      { return holder;
      }
    }

//    // 2009-03-03 mike
//    // Don't delegate to parent context for this- it screws up the path
//    // management.    
//    if (_parentContext!=null)
//    { return _parentContext.getServletFilterChain(servletName);
//    }
    return null;
  }

  /**
   * Deprecated
   * @deprecated
   */  
  @Deprecated
  public Enumeration<Servlet> getServlets()
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return new Vector<Servlet>().elements();
  }

  /**
   * Deprecated
   * @deprecated
   */
  @Deprecated
  public Enumeration<String> getServletNames()
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return new Vector<String>().elements();
  }

  /**
   * Deprecated
   *@deprecated
   */
  @Deprecated
  public Servlet getServlet(String name)
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return null;
  }

  /**
   * Indicate support for servlet api 2.4
   */
  public int getMajorVersion()
  { return 2;
  }

  /**
   * Indicate support for servlet api 2.4
   */
  public int getMinorVersion()
  { return 4;
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
    return _docRootDir.toURI().resolve(name.substring(1)).toURL();
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
	 *   a server path. The specified path parameter is always interpreted as
	 *   relative to this context
	 */
	public String getRealPath(String rawUri)
  { 
	  if (rawUri==null)
	  { return null;
	  }
    String uri=URLEncoder.decode(rawUri);
    if (uri==null)
    { 
      log.log(Level.WARNING,"Undecodable URI "+rawUri);
      return null;
    }
    if (uri.length()>0 && uri.charAt(0)=='/')
    { uri=uri.substring(1);
    }


    if (isPathBounded(uri))
    { 
      File relFile
        =new File(_docRootDir
                  ,new Path(uri,'/').format(File.separatorChar)
                  );
      String realPath
        =relFile.getAbsolutePath();
      if (relFile.isDirectory() && !realPath.endsWith(File.separator))
      { realPath=realPath+File.separator;
      }
      if (_server.getDebugAPI())
      { log.fine(realPath);
      }
      return realPath;
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
      log.log(Level.SEVERE,"No SessionManager available for root context");
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

  public SimpleFilterChain chainGlobalFilters
    (AbstractHttpServletRequest request,SimpleFilterChain endChain)
    throws ServletException
  { 
    
    if (_parentContext!=null)
    { endChain=_parentContext.chainGlobalFilters(request,endChain);
    }
    
    String relativeURI
      =request.getRequestURI().substring(request.getContextPath().length());
    
    if (_filterMappings!=null)
    {
      for (FilterMapping mapping : _filterMappings)
      { 
        if (mapping.isGlobal()
            && mapping.matchesDispatch(request.getSource())
            && mapping.matchesPattern(relativeURI)
           )
        { 
          SimpleFilterChain next
            =new SimpleFilterChain
              (_filterMap.get(mapping.getName()).getFilter());
          next.setContext(this);
          endChain=endChain.chain(next);
        }
      }
    }
    return endChain;
    
  }
  
  private SimpleFilterChain chainLocalFilters
    (AbstractHttpServletRequest request,SimpleFilterChain endChain)
    throws ServletException
  { 
    
    String relativeURI
      =request.getRequestURI().substring(request.getContextPath().length());
    
    if (_filterMappings!=null)
    {
      for (FilterMapping mapping : _filterMappings)
      { 
        if (mapping.matchesDispatch(request.getSource())
            && mapping.matchesPattern(relativeURI)
           )
        { 
          SimpleFilterChain next
            =new SimpleFilterChain
              (_filterMap.get(mapping.getName()).getFilter());
          next.setContext(this);
          endChain=endChain.chain(next);
        }
      }
    }
    return endChain;
    
  }

  /**
   * <p>Return the entire filter chain, or null if no Servlet was found
   * </p>
   * 
   * @param request
   * @return
   * @throws ServletException
   */
  protected FilterChain getFilterChainForRequest
    (AbstractHttpServletRequest request)
    throws ServletException
  {
    
    
    SimpleFilterChain end=new SimpleFilterChain();
    FilterChain filterChain=end;
    
    if (_parentContext!=null)
    { end=_parentContext.chainGlobalFilters(request,end);
    }
    
    end=chainLocalFilters(request,end);
    
    
    if (controller!=null)
    {
      // Insert the AutoFilter controller after the declared filters
      SimpleFilterChain controllerChain
        =new SimpleFilterChain(controller);
      controllerChain.setContext(this);
      end=end.chain(controllerChain);
    }
    
    // Insert the servlet endpoint, if there is one
    FilterChain next=getServletChain(request);
    if (next==null)
    { return null;
    }
    else
    {
      if (end!=null)
      { end.setNext(next);
      }
    
      return filterChain;
    }
    
  }
  
  protected FilterChain getServletChain
    (AbstractHttpServletRequest request)
    throws ServletException
  { 
    // At this point, request.getPathInfo() will contain everything after the
    //  context path.
    //
    // Determine whether a Servlet is mapped to the part of the 
    //   request path immediately after the context path, if any.
    //
    // All requests paths 'contained' within this path will be handled
    //   by the Servlet.
    //
    String servletAlias=null;
    String servletPath=null;
    
    if (debug)
    { 
      log.fine
        ("Locating servlet chain: contextPath="
        +request.getContextPath()
        +" pathInfo="+request.getPathInfo()
        );
    } 
    
    if (request.getPathInfo()!=null)
    {
      servletAlias
        =new Path(request.getPathInfo(),'/').firstElement();
    }
    
    if (servletAlias!=null)
    {
      if (request.getPathInfo().length()>servletAlias.length())
      {
        // There is a path following the servlet name
        servletPath
          =request.getPathInfo().substring
            (0,servletAlias.length()+1);
      }
      else
      { 
        // The servlet path is simply the path info
        servletPath=request.getPathInfo();
      }
    }
      
    if (debug)
    { log.log(Level.DEBUG,"Checking servlet map for '"+servletAlias+"'");
    }
    String servletName=getServletNameForAlias(servletAlias);
    if (servletName==null)
    { 
      // No servlet mapping for the path component
      servletPath="/";
      servletName=_rootServletName;
    }
    
    if (servletName!=null)
    { 
      if (debug)
      { log.log(Level.DEBUG,"Mapped to servlet named '"+servletName+"'");
      }
      return bindRequestToServletChain(request,servletName,servletPath);
    }

    
    // No explicit servlet mapping, so resolve using file system and
    //   default servlet. This sets the servlet path to the pathInfo
    //   (whatever follows the context path),
    //   as a default whether or not we find a preset servlet.
    
    
    String realPath;
    if (request.getPathInfo()!=null)
    { realPath=getRealPath(request.getPathInfo());
    }
    else
    { realPath=getRealPath("/");
    }
    
    if (realPath==null)
    { 
      log.info("Could not map "+request.getPathInfo()+" to a real path");
      return null;
    }
    
    // Try to complete a directory reference by locating a welcome file
    //   in the file system
    if (isDirectory(realPath))
    { 
            
      String uri=null;
      if (_welcomeFileList!=null)
      { 
        for (String filename : _welcomeFileList)
        { 
          uri=checkFilesystemCompletion(request.getPathInfo(),filename);
          if (uri!=null)
          { break;
          }
        }
      }
      else
      {
        // Legacy behavior
        
        if (_defaultUriCompletion!=null)
        { 
          uri=checkFilesystemCompletion
            (request.getPathInfo(),_defaultUriCompletion);
        }
        if (uri==null)
        { uri=checkFilesystemCompletion(request.getPathInfo(),"index.html");
        }
        if (uri==null)
        { uri=checkFilesystemCompletion(request.getPathInfo(),"index.htm");
        }
      }
      
      if (uri!=null)
      { 
        if (debug)
        { log.fine("Rewriting request URI for default completion "+uri);
        }
        // Use determined uri completion.
        request.updateURI(request.getContextPath()+uri.toString());
        request.updateContextPath(getContextPath());
        realPath=getRealPath(request.getPathInfo());
      }

      
    }

    if (debug)
    { log.log(Level.DEBUG,"Locating interpreter for real path "+realPath);
    }
    
    String fileType=getFileType(realPath);

//    // 2009-02-28 mike : Moved directory redirect to the file servlet    
//    if (fileType==null)
//    {
//      if (!request.getRequestURI().endsWith("/") && isDirectory(realPath))
//      { 
//        // Force a redirect to directory
//        return _directoryRedirectFilterChain;
//      }
//    }
    
    servletName=getServletNameForRequestType(fileType);
    if (servletName!=null)
    { 
      FilterChain filterChain=getServletFilterChain(servletName);
      
      if (filterChain==null)
      { throw new ServletException("Servlet '"+servletName+"' not found.");
      }

      
      // This will either be a path or may be empty if the intrinsic default
      //   servlet is returned. In any case, we're using the rules for 
      //   an extension mapping which says the pathInfo is null and the
      //   servlet path is used.
      if (debug)
      { log.log(Level.DEBUG,"Updating servletPath to "+request.getPathInfo());
      }
      request.updateServletPath(request.getPathInfo());
      
      if (debug)
      { 
        log.log(Level.DEBUG,"Using servlet '"+servletName+"' for servletPath "
                  +request.getServletPath()+", file "+getRealPath(request.getServletPath())
                  );
      }
        
      return filterChain;
       
    }
    else
    { 
      if (debug)
      { log.log(Level.DEBUG,"Unable to map servlet to suffix '"+fileType+"'");
      }
      return null;
    }
    
  }

  /**
   *  Called when a servlet is mapped to a URI segment using a specific
   *    servlet path
   *  
   * @param request
   * @param servletName
   * @param servletPath
   * @return
   * @throws ServletException
   */
  private FilterChain bindRequestToServletChain
    (AbstractHttpServletRequest request
    ,String servletName
    ,String servletPath
    )
    throws ServletException
  {

    FilterChain filterChain=getServletFilterChain(servletName);
    if (filterChain==null)
    { throw new ServletException("Servlet '"+servletName+"' not found.");
    }
    request.updateServletPath(servletPath);
    
    if (debug)
    { 
      log.log(Level.DEBUG," contextPath="+request.getContextPath()
                +" servletPath="+request.getServletPath()
                +" pathInfo="+request.getPathInfo()
                );
    }
    return filterChain;
  }
  
  private String checkFilesystemCompletion(String servletPath,String completion)
  {
    if (servletPath==null)
    { servletPath="/";
    }
    
    StringBuilder urib=new StringBuilder(64);
    urib.append(servletPath)
      .append(servletPath.endsWith("/")?"":"/")
      .append(completion);
    String uri=urib.toString();
    
    
    String realPath=getRealPath(uri);

    if (debug)
    { log.log(Level.DEBUG,"Checking completion '"+realPath+"' for uri "
                +uri
                );
    }
    if (realPath!=null && new File(getRealPath(uri)).exists())
    { return uri;
    }
    return null;
    
  }


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
        log.log(Level.SEVERE
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


  public String mapMimeType(String filetype)
  { 
    String mimeType=null;
    if (filetype!=null)
    { 
      if (_mimeMap!=null)
      { mimeType=_mimeMap.get(filetype);
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
      if (_prefixServletNameMap!=null)
      { servletName=_prefixServletNameMap.get(alias);
      }
      if (servletName==null
          && _parentContext!=null
          )
      { servletName=_parentContext.getServletNameForAlias(alias);
      }
    }
    return servletName;
  }

  /**
   * Maps the specified file extension to a servlet name.
   */
  public String getServletNameForRequestType(String type)
  { 
    
    String servletName=null;
    if (type!=null)
    {
      if (_suffixServletNameMap!=null)
      { 
        servletName=_suffixServletNameMap.get(type);
        if (servletName!=null
            && debug
            )
        { 
          log.log
            (Level.DEBUG
            ,"Request mapped to servlet "+servletName
            );
        }
          
      }
      if (servletName==null
          && _parentContext!=null
          && _defaultServletName==null
          && _rootServletName==null
          )
      { 
        servletName=_parentContext.getServletNameForRequestType(type);
      }
    }
    if (servletName==null)
    { 
      if (_defaultServletName!=null
          && debug
          )
      { 
        log.log
          (Level.DEBUG
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
  
  
  public String getServletContextName()
  { return _servletContextName;
  }

    /**
   * Returns a directory-like listing of all the paths to resources within
   *  the web application whose longest sub-path matches the supplied path
   *  argument. Paths indicating subdirectory paths end with a '/'. The
   *  returned paths are all relative to the root of the web application and
   *  have a leading '/'. 
   */
  public Set<String> getResourcePaths(String path)
  {
    try
    {
      if (!path.startsWith("/"))
      { return null;
      }
      path=path.substring(1);
      Resource dirResource
        =Resolver.getInstance().resolve(_docRootDir.toURI().resolve(path));
      
      
      LinkedHashSet<String> set=new LinkedHashSet<String>();
      if (dirResource.exists() && dirResource.asContainer()!=null)
      {
        Search search=new Search();
        URI rootURI=dirResource.getURI();
        search.setRootURI(rootURI);
        List<Resource> results=search.list();

        for (Resource r:results)
        { 
          String result="/"+path+rootURI.relativize(r.getURI()).toString();
          set.add(result);
          
//          log.fine(path+" : "+result);
        }
      }
      return set;
    }
    catch (IOException x)
    { return null;
    }

  }

  //////////////////////////////////////////////////////////////////////////
  //
  // Configuration methods
  //
  //////////////////////////////////////////////////////////////////////////
  
  public void setServer(HttpServer server)
  { _server=server;
  }
  
  public HttpServer getServer()
  { return _server;
  }
  
  public void setDebugWAR(boolean debugWAR)
  { this.debugWAR=debugWAR;
  }
  
  public void setDebug(boolean debug)
  { this.debug=debug;
  }
  
  public boolean isDebug()
  { return debug;
  }
  
  public void setAllowedIpFilter(AddressSet val)
  { _allowedIpFilter=val;
  }

  public void setDeniedIpFilter(AddressSet val)
  { _deniedIpFilter=val;
  }

  public void setUseURLClassLoader(boolean val)
  { this.useURLClassLoader=val;
  }
  
  public HttpServiceContext getParentContext()
  { return _parentContext;
  }
  
  public void installMeter(Meter meter)
  {
    _meter=meter;
    _requestsRegister=_meter.createRegister(SimpleHttpServiceContext.class,"requests");
    
  }
  
  /**
   * Specify the HttpSessionManager that will manage sessions
   *   for this context and its children
   */
  public void setSessionManager(HttpSessionManager sessionManager)
  { _sessionManager=sessionManager;
  }
  
  public void setDocumentRoot(String root)
  { 
    _docRootDir=new File(new Path(root,'/').format(File.separatorChar));
    if (!root.endsWith("/") && _docRootDir.isDirectory())
    { root=root+"/";
    }
    _docRootURI=new File(root).toURI();
  }

  
  public URI getDocumentRootURI()
  { return _docRootURI;
  }
  
  public void setDefaultServletName(String servletName)
  { _defaultServletName=servletName;
  }

  public void setRootServletName(String servletName)
  { _rootServletName=servletName;
  }
  
  public void addWelcomeFile(String welcomeFile)
  {
    if (_welcomeFileList==null)
    { _welcomeFileList=new ArrayList<String>();
    }
    _welcomeFileList.add(welcomeFile);
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
  


  public String getContextPath()
  { return _contextPath;
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
    {
      _servletMap.put(holder.getServletName(),holder);
      holder.setServiceContext(this);
    }
    
  }
  
  public void setFilters(FilterHolder[] filterHolders)
  {
    _filterMap=new HashMap<String,FilterHolder>();
    for (FilterHolder filterHolder: filterHolders)
    { addFilter(filterHolder);
    }
  }
  
  /**
   * Specify the servlet map, which maps names to
   *   SerlvetHolders.
   *   
   *@deprecated Use setServlets()
   */
  @Deprecated
  public void setServletMap(HashMap<String,ServletHolder> servletMap)
  {
    if (log.canLog(Level.DEBUG))
    { log.log(Level.DEBUG,servletMap.toString());
    }

    _servletMap=servletMap;
  }

  public void addFilter(FilterHolder filterHolder)
  {
    if (_filterMap==null)
    { _filterMap=new HashMap<String,FilterHolder>();
    }
    _filterMap.put(filterHolder.getFilterName(),filterHolder);
    filterHolder.setServiceContext(this);
  }
   
  public void setFilterMappings(FilterMapping[] mappings)
  { 
    _filterMappings=new ArrayList<FilterMapping>();
    for (FilterMapping mapping:mappings)
    { _filterMappings.add(mapping);
    }
  }
  
  public void addFilterMapping(FilterMapping mapping)
  { 
    if (_filterMappings==null)
    { _filterMappings=new ArrayList<FilterMapping>();
    }
    _filterMappings.add(mapping);
  }
  
  public void addFilterMapping(String name,String urlPattern)
  { addFilterMapping(new FilterMapping(urlPattern,name,true,false,false));
  }
  
  public void addServlet(ServletHolder servletHolder)
  {
    if (_servletMap==null)
    { _servletMap=new HashMap<String,ServletHolder>();
    }
    _servletMap.put(servletHolder.getServletName(),servletHolder);
    servletHolder.setServiceContext(this);
  }
  
  public void setServletMapping(String urlPattern,String name)
  { 
    if (_prefixServletNameMap==null)
    { _prefixServletNameMap=new HashMap<String,String>();
    }
    setServletMapping(new PatternMapping(urlPattern,name));
    
  }
  
  
  public void setServletMapping(PatternMapping mapping)
  {
    String[] patterns=mapping.getURLPatterns();
    for (String pattern:patterns)
    {
      if (debug)
      { log.fine("Servlet mapping "+pattern+" -> "+mapping.getName());
      }
       
      if (pattern.startsWith("*."))
      { 
        if (_suffixServletNameMap==null)
        { _suffixServletNameMap=new HashMap<String,String>();
        }

        _suffixServletNameMap.put
          (pattern.substring(2)
          ,mapping.getName()
          );
      }
      else if (pattern.equals("/"))
      {  _defaultServletName=mapping.getName();
      }
      else if (pattern.startsWith("/")
                && pattern.endsWith("/*")
                )
      { 
        if (_prefixServletNameMap==null)
        { _prefixServletNameMap=new HashMap<String,String>();
        }
      
        // XXX Doesn't conform to spec yet
        _prefixServletNameMap.put
          (pattern.substring
            (1,pattern.length()-2)
          ,mapping.getName()
          );
      }
      else
      { 
        // XXX Doesn't conform to spec yet
        String name=mapping.getName();
        if (name.startsWith("/"))
        { name=name.substring(1);
        }
        _prefixServletNameMap.put
          (pattern,name);
      }
    }
  
  }
  
  
  /**
   * Specify how request URLs map to Servlets 
   * 
   * @param servletMappings
   */
  public void setServletMappings(PatternMapping[] servletMappings)
  {
    _prefixServletNameMap=new HashMap<String,String>();
    _suffixServletNameMap=new HashMap<String,String>();
    for (PatternMapping mapping: servletMappings)
    { setServletMapping(mapping);
    }
    
    
  }

  /**
   * <p>Assign a value to an initialization parameter readable via
   *   the ServletContext.getInitParameter(String name) method
   * </p>
   *  
   * @param name
   * @param value
   */
  @Override
  public void setInitParameter(String name,String value)
  { 
    if (debug)
    { log.fine(""+name+" = "+value);
    }
    _initParameters.put(name, value);
  }
  
  /**
   * Specify the handler map, which maps request
   *   'types' to servlet names.
   *@deprecated Use setServletMappings
   */
  @Deprecated
  public void setServletAliasMap(HashMap<String,String> aliasMap)
  { 
    _prefixServletNameMap=aliasMap;
  }

  /**
   * Specify the handler map, which maps request
   *   'types' to servlet names.
   */
  public void setTypeHandlerMap(HashMap<String,String> handlerMap)
  {
    if (log.canLog(Level.DEBUG))
    { log.log(Level.DEBUG,handlerMap.toString());
    }

    _suffixServletNameMap=handlerMap;
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

  public void addListenerClass(String className)
  { 
    if (_listenerClassNames==null)
    { _listenerClassNames=new ArrayList<String>();
    }
    _listenerClassNames.add(className);
  }
  
  public void addServletContextAttributeListener
    (ServletContextAttributeListener listener)
  {
    if (_attributeListeners==null)
    { _attributeListeners=new ArrayList<ServletContextAttributeListener>();
    }
    _attributeListeners.add(listener);
  }
  
  public void addServletContextListener(ServletContextListener listener)
  {
    if (_listeners==null)
    { _listeners=new ArrayList<ServletContextListener>();
    }
    _listeners.add(listener);
  }
  
  public void addServletRequestAttributeListener
    (ServletRequestAttributeListener listener)
  {
    if (_requestAttributeListeners==null)
    { _requestAttributeListeners=new ArrayList<ServletRequestAttributeListener>();
    }
    _requestAttributeListeners.add(listener);
    
  }

  public void addServletRequestListener(ServletRequestListener listener)
  {
    if (_requestListeners==null)
    { _requestListeners=new ArrayList<ServletRequestListener>();
    }
    _requestListeners.add(listener);
  }
  
  public void setVirtualHostName(String hostName)
  { this.virtualHostName=hostName;
  }
  
  /////////////////////////////////////////////////////////////////////////
  //
  // Startup sequence
  //
  /////////////////////////////////////////////////////////////////////////
  
  /**
   * Prepare for request handling
   */
  @Override
  public void start()
    throws LifecycleException
  { 
    if (_initialized)
    { throw new RuntimeException("Servlet context already initialized");
    }
    _initialized=true;
    if (_parentContext!=null)
    { _server=_parentContext.getServer();
    }
    
    if (!_docRootDir.isAbsolute())
    { 
      if (_parentContext!=null)
      {
        setDocumentRoot
          (_parentContext.getDocumentRootURI()
            .resolve(getDocumentRootURI())
            .getPath()
          );
      }
      else
      { setDocumentRoot(_docRootDir.toURI().getPath());
      }
    }
    
    log.info
      (getLogPrefix()+": Starting. root="+_docRootDir);

//    try
//    { _baseUrl=new URL("http",_hostName,_port,"/");
//    }
//   catch (MalformedURLException x)
//    { 
//      log.log(Level.ERROR,x.toString());
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
        { log.log(Level.WARNING,"Error loading default mime type map: "+x.toString());
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
        { log.log(Level.WARNING,"Error loading configured mime type map: "+x.toString());
        }
      }
    }
    if (_attributes==null)
    { 
      if (_parentContext==null)
      { 
        log.log
          (Level.INFO
          ,getClass().getName()
            +" serving "+_docRootDir.getPath()+" is attributes root"
          );
        _attributes=new Hashtable<String,Object>();
      }
    }

    if (_prefixServletNameMap!=null 
        && debug
        )
    { 
      log.log(Level.DEBUG,getLogPrefix()+": Servlet alias map: "
          +_prefixServletNameMap.toString());
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
    { startInContext();
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
    _running=true;
    log.info(getLogPrefix()+": Started");
  }
  
  /**
   * <p>Start contextual elements which depend on the WAR ClassLoader
   *   being active.
   * </p>
   */
  private void startInContext()
    throws LifecycleException
  {
    loadWebXML();
    startListeners();
    if (_listeners!=null)
    { 
      for (ServletContextListener listener:_listeners)
      { listener.contextInitialized(new ServletContextEvent(this));
      }
    }
    startFilters();
    startServlets();
  }
  
  private void loadWebXML()
    throws LifecycleException
  {   
    try
    {
      Resource docRoot=Resolver.getInstance().resolve(_docRootDir.toURI());
      if (docRoot.asContainer()!=null)
      {
        Resource warRoot=docRoot.asContainer().getChild("WEB-INF");
        if (warRoot.exists() && warRoot.asContainer()!=null)
        { 
          Resource webxml=warRoot.asContainer().getChild("web.xml");
          if (webxml.exists())
          { new WebXMLConfigurator(this).read(webxml);
          }
        }
      }
      else
      { 
        if (debug)
        { log.debug("Resource "+docRoot+" is not a container");
        }
      }
    }
    catch (Exception x)
    { throw new LifecycleException("Error loading web.xml",x);
    }
  } 
  
    

  private void startServlets()
  { 
    if (_servletMap!=null)
    {
      TreeSet<ServletHolder> holders
        =new TreeSet<ServletHolder>
          (new Comparator<ServletHolder>()
          {
            @Override
            public int compare(ServletHolder o1, ServletHolder o2)
            { 
              return Integer.valueOf(o1.getLoadOnStartup())
                .compareTo(o2.getLoadOnStartup());
            }
          }
          );

      for (ServletHolder holder:_servletMap.values())
      { holders.add(holder);
      }
      
      for (ServletHolder holder:holders)
      { 
        holder.start();
        _servletStack.push(holder);
      }
    }  
  }

  private void startFilters()
  {
    boolean startedController=false;
    
    if (_filterMap!=null)
    { 
      for (FilterHolder holder:_filterMap.values())
      { 
        holder.start();
        try
        {
          if (holder.getFilter() instanceof Controller)
          { startedController=true;
          }
        }
        catch (ServletException x)
        { log.log(Level.WARNING,"Unexpected exception getting filter",x);
        }
      }
      
    }
    
    if (!startedController)
    { startControllerFilter();
    }
  }

  private void startControllerFilter()
  {
    controller=new Controller();
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
  
  private void startListeners()
    throws LifecycleException
  {
    if (_listenerClassNames!=null)
    { 
      for (String className:_listenerClassNames)
      { 
        try
        {
          Class<?> listenerClass=Class.forName
            (className,true,Thread.currentThread().getContextClassLoader());
          Object listener=listenerClass.newInstance();
          if (listener instanceof ServletContextListener)
          { addServletContextListener((ServletContextListener) listener);
          }
          if (listener instanceof ServletContextAttributeListener)
          { 
            addServletContextAttributeListener
              ((ServletContextAttributeListener) listener);
          }
          if (listener instanceof HttpSessionListener)
          { log.warning("HttpSessionListener unsupported "+listener);
          }
          if (listener instanceof HttpSessionAttributeListener)
          { log.warning("HttpSessionAttributeListener unsupported "+listener);
          }
          if (listener instanceof ServletRequestListener)
          { addServletRequestListener((ServletRequestListener) listener);
          }
          if (listener instanceof ServletRequestAttributeListener)
          { 
            addServletRequestAttributeListener
              ((ServletRequestAttributeListener) listener);
          }
        }
        catch (ClassNotFoundException x)
        { throw new LifecycleException("Listener class not found",x);
        }
        catch (InstantiationException x)
        { throw new LifecycleException("Error instantiating listener class",x);
        }
        catch (IllegalAccessException x)
        { throw new LifecycleException("Error instantiating listener class",x);
        }
      }
    }
  }
  
  private void loadWAR()
  {
    try
    {
      Resource docRoot=Resolver.getInstance().resolve(_docRootDir.toURI());
      if (docRoot.asContainer()!=null)
      {
        Resource warRoot=docRoot.asContainer().getChild("WEB-INF");
        if (warRoot.exists())
        { 
          if (!useURLClassLoader)
          {
            if (debug)
            {
              log.log
                (Level.DEBUG,"Loading WARClassloader from "+warRoot.getURI());
            }            
            WARClassLoader contextClassLoader=new WARClassLoader(warRoot);
            if (debugWAR)
            { contextClassLoader.setDebug(true);
            }
            contextClassLoader.start();
            this.contextClassLoader=contextClassLoader;
          }
          else
          {
            ServletURLClassLoader contextClassLoader
              =new ServletURLClassLoader(warRoot);
            this.contextClassLoader=contextClassLoader;
          }
        }
        else
        {
          if (debug)
          {
            log.log
              (Level.DEBUG,warRoot.getURI()+" does not exist "
                +", not loading WAR ClassLoader"
             );
          }
        }
      }
      else
      { 
        log.log
          (Level.SEVERE,"Document root "+_docRootDir+" is not a valid directory"
              +", not loading WAR ClassLoader"
           );
      }
    }
    catch (IOException x)
    { log.log(Level.SEVERE,"Error loading WAR ClassLoader: "+x.toString());
    }
    catch (LifecycleException x)
    { log.log(Level.SEVERE,"Error loading WAR ClassLoader: "+x.toString());
    }
  }
  
  /////////////////////////////////////////////////////////////////////////
  //
  // Shutdown sequence
  //
  ////////////////////////////////////////////////////////////////////////

  public void stop()
    throws LifecycleException
  { 
    _running=false;
    
    
    
    stopServlets();
    stopFilters();


    
    if (localAccessLog!=null)
    { 
      try
      { localAccessLog.stop();
      }
      catch (LifecycleException x)
      { log.log(Level.SEVERE,"Error starting access log",x);
      }

    }
    
    if (_sessionManager!=null 
        && (_sessionManager instanceof SimpleHttpSessionManager)
        )
    { ((SimpleHttpSessionManager) _sessionManager).stop();
    }
    
    if (_listeners!=null)
    { 
      for (ServletContextListener listener:_listeners)
      { listener.contextDestroyed(new ServletContextEvent(this));
      }
    }
    
    if (contextClassLoader!=null && !useURLClassLoader)
    { 
      try
      { ((WARClassLoader) contextClassLoader).stop();
      }
      catch (LifecycleException x)
      { 
        log.log(Level.WARNING,"Error stopping WAR ClassLoader: "+x.toString());
        x.printStackTrace();
      }
      contextClassLoader=null;
    }
    
  }  
    
  
  public void stopFilters()
    throws LifecycleException
  {
    while (!_filterStack.isEmpty())
    { _filterStack.pop().stop();
    }
    if (controller!=null)
    { 
      controller.destroy();
      controller=null;
    }
  }
  
  public void stopServlets()
    throws LifecycleException
  {
    while (!_servletStack.isEmpty())
    { _servletStack.pop().stop();
    }
  }


}
