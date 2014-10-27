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
import javax.servlet.RequestDispatcher;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSessionAttributeListener;
import javax.servlet.http.HttpSessionListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
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
import spiralcraft.util.URIUtil;
import spiralcraft.util.thread.ThreadLocalStack;
import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.Resource;
import spiralcraft.vfs.UnresolvableURIException;
import spiralcraft.vfs.batch.Search;
import spiralcraft.vfs.file.FileResource;
import spiralcraft.classloader.Archive;
import spiralcraft.common.ContextualException;
import spiralcraft.common.LifecycleException;
import spiralcraft.common.declare.Declarable;
import spiralcraft.common.declare.DeclarationInfo;
import spiralcraft.lang.BindException;
import spiralcraft.lang.Focus;
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
import spiralcraft.servlet.PublicLocator;
import spiralcraft.servlet.autofilter.Controller;
import spiralcraft.net.http.AcceptHeader;

public class SimpleHttpServiceContext
  implements HttpServiceContext
            ,Meterable
            ,Declarable
{
  protected static final ClassLog log
    =ClassLog.getInstance(SimpleHttpServiceContext.class);
  private static final String version="1.0pre1";
  
  //private static final String DEBUG_GROUP
  //  =SimpleHttpServiceContext.class.getName();

  private File _docRootDir;
  private URI _docRootURI;
  
  private HttpSessionManager _sessionManager;
  private String _hostName;
  private String _contextPath="";
  private String _defaultServletName=null; // Serves the specified URI (servletPath)
  private String _rootServletName=null; // Serves the whole context (gets pathInfo)
  private HttpServiceContext _parentContext=null;
  private HashMap<String,ServletHolder> _servletMap=null;
  private HashMap<String,String> _suffixServletNameMap=null;
  private HashMap<String,String> _prefixServletNameMap=null;
  private ArrayList<FilterMapping> _filterMappings=null;
  private HashMap<String,FilterHolder> _filterMap=null;
  private ArrayList<ErrorPage> _errorPages=new ArrayList<ErrorPage>();
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
  protected Focus<?> focus;
  
  private boolean exposeContainerFocus;
  
  private String sessionCookieName;
  private String secureSessionCookieName;
  private String sessionParameterName;
  private Boolean cookiesArePortSpecific;
  private Integer securePort;
  private Integer standardPort;
  
  private SecurityConstraint[] securityConstraints;
  
  private Resource[] libraryResources;

  private DeclarationInfo declarationInfo;
  
  private final ThreadLocalStack<ServiceStatus> statusStack
    =new ThreadLocalStack<>();

  /////////////////////////////////////////////////////////////////////////
  //
  // Service Methods
  //
  /////////////////////////////////////////////////////////////////////////
  
  
  /**
   * Service the request
   */
  @Override
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
      
      boolean topLevel=request instanceof HttpServerRequest;
      
      if (topLevel)
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
      
      serviceWithErrorHandling(request,response,topLevel);
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
  
  protected void serviceInContext
    (final AbstractHttpServletRequest request
    ,final HttpServletResponse response
    ,boolean topLevel
    )
    throws ServletException,IOException
  {
    
    FilterChain filterChain=null;
    
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
    
    try
    {
      filterChain=getFilterChainForRequest(request);

      if (filterChain!=null)
      { 
        fireRequestInitialized(request);
        // This is the main act
        filterChain.doFilter(request,response);
        fireRequestDestroyed(request);
      }
    }
    finally
    {
      if (lastLoader!=null)
      { Thread.currentThread().setContextClassLoader(lastLoader);
      }
      
      if (filterChain==null)
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
    
  }

  protected void serviceWithErrorHandling
    (final AbstractHttpServletRequest request
    ,final HttpServletResponse response
    ,boolean topLevel
    )
    throws ServletException,IOException
  {

    if (preFilter(request,response))
    {
      try
      { 
        ServiceStatus status=new ServiceStatus();
        statusStack.push(status);
        try
        { serviceInContext(request,response,topLevel);
        }
        finally
        { statusStack.pop();
        }
        
        if (status.error!=null)
        { 
          handleError
            (request
            ,(HttpServerResponse) response
            ,status.error.code
            ,status.error.message
            ,status.error.exception
            );
        }
      }
      catch (ServletException x)
      {
        log.log(Level.SEVERE
                ,"ServletException handling "
                  +"http://"+request.getHeader("Host")+request.getRequestURI()
                  +": "+x.toString()
                ,x
                );
        if (topLevel)
        {
          handleError
            (request
            ,(HttpServerResponse) response
            ,500
            ,"Internal Server Error"
            ,x
            );
        }
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
        if (topLevel)
        {
          handleError
            (request
            ,(HttpServerResponse) response
            ,500
            ,"Internal Server Error"
            ,x
            );
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
  
  
  @Override
  public void handleError
    (AbstractHttpServletRequest request
    ,HttpServerResponse response
    ,int code
    ,String message
    ,Throwable exception
    )
    throws IOException
  {
    if (statusStack.size()>0)
    { 
      statusStack.get().error=new HttpError(code,message,exception);
      return;
    }
        
    if (request.getSource()==AbstractHttpServletRequest.RequestSource.ERROR)
    { 
      log.warning("Error sending error page: "+request.getRequestURI());
      response.setStatus(500);
      return;
    }
    
    String errorURI=null;
    
    // Go through error pages
    
    try
    { 
      AcceptHeader acceptHeader
        =AcceptHeader.fromString(request.getHeader("Accept"));
      if (acceptHeader!=null && !acceptHeader.accepts("text","html"))
      { 
        response.setStatus(code);
        response.flushBuffer();
      }
    }
    catch (Exception x)
    { log.log(Level.WARNING,"Error parsing accepts header",x);
    }
    
    // Go through codes
    if (code>0)
    {
      for (ErrorPage errorPage: _errorPages)
      {
        if (errorPage.getErrorCode()==code)
        { 
          errorURI=errorPage.getLocation().toString();
          break;
        }
      }
    }
    
    // Go through exceptions
    if (errorURI==null && exception!=null)
    {
      // Go through codes
      for (ErrorPage errorPage: _errorPages)
      {
        if (errorPage.getExceptionType()!=null
            && errorPage.getExceptionType()
              .isAssignableFrom(exception.getClass())
           )
        { 
          errorURI=errorPage.getLocation().toString();
          break;
        }
      }
      
      if (errorURI==null 
          && exception instanceof ServletException
          && ((ServletException) exception).getRootCause()!=null
          )
      {
        exception=((ServletException) exception).getRootCause();
        for (ErrorPage errorPage: _errorPages)
        {
          if (errorPage.getExceptionType()!=null
              && errorPage.getExceptionType()
                .isAssignableFrom(exception.getClass())
             )
          { 
            errorURI=errorPage.getLocation().toString();
            break;
          }
        }
        
        
      }
      
      
    }
    
    
    // dispatch wrapped request, wrapped response
    if (errorURI!=null)
    {
    
      response.setStatus(code);
      if (debug)
      { log.fine("Sending error for status: "+response.getStatus());
      }
      
      ServerRequestDispatcher dispatcher
        =(ServerRequestDispatcher) getRequestDispatcher(errorURI);
      try
      {
        dispatcher.sendError(request,response,code,message,exception);
        return;
      }
      catch (ServletException x)
      { 
        if (exception!=null)
        { log.log(Level.WARNING,"Unhandled exception",x);
        }
        log.log(Level.WARNING,"Error handling error",x);
      }
    }

    response.setContentType("text/html");
    response.setStatus(code);

    PrintWriter out = response.getWriter();
    out.println("<html><head><title>"
                +code+"-"+response.getReason()+"</title><body>"+message
                +"</body></html>");
    out.flush();
    
  }
  
  /**
   * Perform any actions or protocol specifics
   *   before handing the request off to a servlet.
   */
  @Override
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
    
    return checkSecurityConstraints(request,response);
  }
  
  private boolean checkSecurityConstraints
    (AbstractHttpServletRequest request
    ,HttpServletResponse response
    )
    throws IOException,ServletException
  { 
    if (securityConstraints!=null)
    {
      for (SecurityConstraint constraint: securityConstraints)
      { 
        if (constraint.matches(request.getMethod(),request.getRequestURI()))
        {
          if (constraint.getRequireSecureChannel())
          { 
            if (!request.isSecure())
            {
              URI requestURI
                =URI.create("https://"
                        +request.getServerName()
                        +(request.getSecurePort()!=443
                          ?":"+request.getSecurePort()
                          :""
                        )
                        +request.getRequestURI()
                        +( (request.getQueryString()!=null)
                            ?"?"+request.getQueryString()
                            :""
                         )
                        );
              response.sendRedirect(requestURI.toString());
            }
            break; // First matching constraint that requires secure channel
                   // takes precedent
          }
          else if (constraint.getPreferStandardChannel() && request.isSecure())
          {
            URI requestURI
              =URI.create("http://"
                    +request.getServerName()
                    +(request.getStandardPort()!=80
                      ?":"+request.getStandardPort()
                      :""
                    )
                    +request.getRequestURI()
                    +( (request.getQueryString()!=null)
                        ?"?"+request.getQueryString()
                        :""
                     )
                    );
            response.sendRedirect(requestURI.toString());
            
          }
        }
      }
    }
    return true;
  }



  /**
   * Return an attribute
   */
  @Override
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

  @Override
  public void setContextPath(String contextPath)
  { _contextPath=contextPath;
  }
  
  public void setLibraries(Resource[] libraries)
  { this.libraryResources=libraries;
  }
  
  /**
   * Return a list of attribute names
   */
  @Override
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
  @Override
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
  @Override
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

  @Override
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
  
  @Override
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

  @Override
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
  @Override
  public String getServerInfo()
  { return _serverInfo;
  }

  /**
   * Return a request dispatcher for the specified uri path
   */
  @Override
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
  @Override
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
  @Override
  public ServletContext getContext(String uri)
  { 
    throw new UnsupportedOperationException("getContext("+uri+")");
  }

  /**
   * Return the init parameter names
   */
  @Override
  public Enumeration<String> getInitParameterNames()
  { return new IteratorEnumeration<String>(_initParameters.keySet().iterator());
  }

  /**
   * Return an init parameter
   */
  @Override
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
  @Override
  public void log(String msg)
  { 
    if (log.canLog(Level.INFO))
    { 
      log.log(Level.INFO,msg,null,1);
    }
  }

  /**
   * Log an error
   *@deprecated
   */
  @Override
  @Deprecated
  public void log(Exception x,String msg)
  { log.log(Level.SEVERE,msg,x);
  }

  /**
   * Log an error
   */
  @Override
  public void log(String msg,Throwable x)
  { log.log(Level.SEVERE,msg,x);
  }


  public String getLogPrefix()
  { 
    return (virtualHostName!=null?"//"+virtualHostName:"")
       +(getContextPath().length()>0?getContextPath():"/")
       +(getServletContextName()!=null?" \""+getServletContextName()+"\" ":"");
  }


  @Override
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
  @Override
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
  @Override
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
  @Override
  @Deprecated
  public Servlet getServlet(String name)
  { 
    new Exception("Deprecated method invoked").printStackTrace();
    return null;
  }

  /**
   * Indicate support for servlet api 2.4
   */
  @Override
  public int getMajorVersion()
  { return 2;
  }

  /**
   * Indicate support for servlet api 2.4
   */
  @Override
  public int getMinorVersion()
  { return 4;
  }

  /**
   * Return a new URL relative to the base url for this context 
   */
  @Override
  public URL getResource(String name)
    throws MalformedURLException
  { 
    if (!name.startsWith("/"))
    { throw new MalformedURLException(name+" does not start with '/'");
    }
    URL ret=null;
    if (_docRootURI!=null)
    { 
      // XXX: We need to make sure the VFS package is registered as
      //   a URLStreamHandlerFactory for this case to work
      ret=_docRootURI.resolve(name.substring(1)).toURL();
    }
    
    if (_server.getDebugAPI())
    { log.fine("getResource("+name+") returned "+ret);
    }
    return ret;
    
  }

  /**
   * Return a new InputStream for the url relative to the base
   *   url for this context
   */
  @Override
  public InputStream getResourceAsStream(String name)
  { 
    try
    {
      URL resource=getResource(name);
      if (resource!=null)
      { return resource.openConnection().getInputStream();
      }
    }
    catch (IOException x)
    {
      if (_server.getDebugAPI())
      { log.log(Level.FINE,"getResourceAsStream("+name+") IOException",x);
      }
    }
    return null;
  }

	/**
	 * Return the physical path that corresponds to
	 *   a server path. The specified path parameter is always interpreted as
	 *   relative to this context
	 */
	@Override
  public String getRealPath(String rawUri)
  { 
	  if (_docRootDir==null)
	  { 
      if (_server.getDebugAPI())
      { 
        log.fine("getRealPath("+rawUri+") returned null- "
                 +"no document root dir configured"
            );
      }
	    return null;
	  }
	  
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
      { log.fine("getRealPath("+rawUri+") returned "+realPath);
      }
      return realPath;
    }
    else
    { 
      if (_server.getDebugAPI())
      { log.fine("getRealPath("+rawUri+") is not bounded and returned null");
      }
      return null;
    }
  }

  /**
   * Return the session manager
   */
  @Override
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



  @Override
  public String getMimeType(String file)
  { return mapMimeType(getFileType(file));
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

  @Override
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
    String servletPath=null;
    
    if (debug)
    { 
      log.fine
        ("Locating servlet chain: contextPath="
        +request.getContextPath()
        +" pathInfo="+request.getPathInfo()
        );
    } 
    
    String servletName=null;
    String pathInfo=request.getPathInfo();
    if (pathInfo!=null)
    {
      Path path=new Path(pathInfo,'/').trim();
      String pathString=null;
      
      while (path.size()>0
             && (servletName=getServletNameForAlias
                   ( pathString=path.format('/')
                   )
                )==null
             )
      { 
        if (debug)
        { log.log(Level.DEBUG,"Checked servlet map for '"+pathString+"'");
        }   
        path=path.parentPath();
        path=path!=null?path.trim():Path.EMPTY_PATH;
     
      }
      
      if (servletName!=null)
      { 
        if (debug)
        { log.log(Level.DEBUG,"Found servlet map for '"+pathString+"'");
        }   
        
        servletPath='/'+pathString;
      }
    }
          

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
    
    if (realPath!=null)
    { return getServletChainForRealPath(request,realPath);
    }
    else
    { return getServletChainForVirtualPath(request,request.getPathInfo());
    }
  }
  
  /**
   * Find the filter/servlet chain when we have a filesystem path. Performs
   *   directory redirect behavior and directory welcome file completion
   * 
   * @param request
   * @param realPath
   * @return
   * @throws ServletException
   */
  protected FilterChain getServletChainForRealPath
    (AbstractHttpServletRequest request,String realPath)
    throws ServletException
  {
    
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
    
    String servletName=getServletNameForRequestType(fileType);
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
                  +request.getServletPath()+", file "
                  +getRealPath(request.getServletPath())
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
   * Find the filter/servlet chain when we have no associated filesystem
   *   path
   * 
   * @param request
   * @param realPath
   * @return
   * @throws ServletException
   */
  protected FilterChain getServletChainForVirtualPath
    (AbstractHttpServletRequest request,String pathInfo)
    throws ServletException
  {
    


    if (debug)
    { log.log(Level.DEBUG,"Locating interpreter for virtual path "+pathInfo);
    }
    
    String fileType=getFileType(pathInfo);
    
    String servletName=getServletNameForRequestType(fileType);
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
      { log.log(Level.DEBUG,"Updating servletPath to "+pathInfo);
      }
      request.updateServletPath(request.getPathInfo());
      
      if (debug)
      { 
        log.log(Level.DEBUG,"Using servlet '"+servletName+"' for servletPath "
                  +request.getServletPath()
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


  @Override
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

  @Override
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
  @Override
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
  
  
  @Override
  public String getServletContextName()
  { return _servletContextName;
  }

  @Override
  public String getSessionCookieName()
  {
    if (sessionCookieName!=null)
    { return sessionCookieName;
    }
    else if (_parentContext!=null)
    { return _parentContext.getSessionCookieName();
    }
    else
    { return "JSESSIONID";
    }
  }
  
  @Override
  public String getSecureSessionCookieName()
  {
    if (secureSessionCookieName!=null)
    { return secureSessionCookieName;
    }
    else if (_parentContext!=null)
    { return _parentContext.getSecureSessionCookieName();
    }
    else
    { return "JSESSIONTAGSSL";
    }
  }
  
  @Override
  public String getSessionParameterName()
  {
    if (sessionParameterName!=null)
    { return sessionParameterName;
    }
    else if (sessionCookieName!=null)
    { return sessionCookieName;
    }
    else if (_parentContext!=null)
    { return _parentContext.getSessionParameterName();
    }
    else
    { return "jsessionid";
    }
    
  }
  
  @Override
  public boolean getCookiesArePortSpecific()
  { 
    if (cookiesArePortSpecific!=null)
    { return cookiesArePortSpecific;
    }
    else if (_parentContext!=null)
    { return _parentContext.getCookiesArePortSpecific();
    } 
    else
    { return false;
    }
  }
  
  @Override
  public Integer getSecurePort()
  {
    if (securePort!=null)
    { return securePort;
    }
    else if (_parentContext!=null)
    { return _parentContext.getSecurePort();
    }
    else
    { return null;
    }
  }
  
  @Override
  public Integer getStandardPort()
  {
    if (standardPort!=null)
    { return standardPort;
    }
    else if (_parentContext!=null)
    { return _parentContext.getStandardPort();
    }
    else
    { return null;
    }
  }
  
  /**
   * The effective hostname of the server
   */
  @Override
  public String getHostName()
  { 
    if (_hostName!=null)
    { return _hostName;
    }
    else if (virtualHostName!=null)
    { return virtualHostName;
    }
    else if (_parentContext!=null)
    { return _parentContext.getHostName();
    }
    else
    { return null;
    }
  }
  
  /**
   * Returns a directory-like listing of all the paths to resources within
   *  the web application whose longest sub-path matches the supplied path
   *  argument. Paths indicating subdirectory paths end with a '/'. The
   *  returned paths are all relative to the root of the web application and
   *  have a leading '/'. 
   */
  @Override
  public Set<String> getResourcePaths(String path)
  {
    if (_docRootURI==null)
    { return new LinkedHashSet<String>();
    }
    
    try
    {
      if (!path.startsWith("/"))
      { 
        if (_server.getDebugAPI())
        { log.fine("getResourcePaths("+path+"): path does not start with '/'");
        }
        return null;
      }
      
      path=path.substring(1);
      if (!path.endsWith("/"))
      { path=path+"/";
      }
      
      Resource dirResource
        =Resolver.getInstance().resolve
          (_docRootURI.resolve(path));
      
      
      LinkedHashSet<String> set=new LinkedHashSet<String>();
      if (dirResource.exists() && dirResource.asContainer()!=null)
      {
        Search search=new Search();
        URI rootURI=URIUtil.ensureTrailingSlash(dirResource.getURI());
        search.setRootURI(rootURI);
        List<Resource> results=search.list();

        for (Resource r:results)
        { 
          String result="/"+path+rootURI.relativize(r.getURI()).toString();
          set.add(result);
          
//          log.fine(path+" : "+result);
        }
      }
      if (_server.getDebugAPI())
      { log.fine("getResourcePaths("+path+") returned "+set);
      }
      return set;
    }
    catch (IOException x)
    {
      if (_server.getDebugAPI())
      { log.log(Level.FINE,"IOException in getResourcePaths("+path+")",x);
      }
      return null;
    }

  }

  //////////////////////////////////////////////////////////////////////////
  //
  // Configuration methods
  //
  //////////////////////////////////////////////////////////////////////////
  
  @Override
  public void setServer(HttpServer server)
  { _server=server;
  }
  
  @Override
  public HttpServer getServer()
  { return _server;
  }
  
  public void setDebugWAR(boolean debugWAR)
  { this.debugWAR=debugWAR;
  }
  
  public void setDebug(boolean debug)
  { this.debug=debug;
  }
  
  @Override
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
  
  @Override
  public HttpServiceContext getParentContext()
  { return _parentContext;
  }
  
  @Override
  public void installMeter(Meter meter)
  {
    _meter=meter;
    _requestsRegister=_meter.createRegister(SimpleHttpServiceContext.class,"requests");
    
  }
  
  public void setSessionParameterName(String sessionParameterName)
  { this.sessionParameterName=sessionParameterName;
  }

  public void setSessionCookieName(String sessionCookieName)
  { this.sessionCookieName=sessionCookieName;
  }
  
  public void setSecureSessionCookieName(String sessionCookieName)
  { this.secureSessionCookieName=sessionCookieName;
  }
  
  public void setCookiesArePortSpecific(boolean cookiesArePortSpecific)
  { this.cookiesArePortSpecific=cookiesArePortSpecific;
  }
  
  public void setSecurePort(int securePort)
  { this.securePort=securePort;
  }
  
  public void setStandardPort(int standardPort)
  { this.standardPort=standardPort;
  }
  
  /**
   * Specify the HttpSessionManager that will manage sessions
   *   for this context and its children
   */
  public void setSessionManager(HttpSessionManager sessionManager)
  {
    _sessionManager=sessionManager;
    _sessionManager.setServletContext(this);
  }
  
  public void setDocumentRootURI(URI uri)
  {
    try
    {
      FileResource resource=Resolver.getInstance().resolve(uri)
        .unwrap(FileResource.class);
      if (resource!=null)
      { setDocumentRoot(resource.getFile().toURI().getPath());
      }
      else
      { 
        _docRootDir=null;
        _docRootURI
          =URIUtil.ensureTrailingSlash
            (Resolver.getInstance().resolve(uri).getURI()
            );
        setAttribute("spiralcraft.context.root.uri",_docRootURI);
      }
    }
    catch (UnresolvableURIException e)
    { throw new IllegalArgumentException(e);
    }
    
  }
  
  public void setDocumentRoot(String root)
  { 
    _docRootDir=new File(new Path(root,'/').format(File.separatorChar));
    if (!root.endsWith("/") && _docRootDir.isDirectory())
    { root=root+"/";
    }
    _docRootURI=new File(root).toURI();
    setAttribute("spiralcraft.context.root.uri",_docRootURI);
  }

  
  @Override
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
  
  public void addErrorPage(ErrorPage errorPage)
  { _errorPages.add(errorPage);
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
  


  @Override
  public String getContextPath()
  { return _contextPath;
  }

  /**
   * Specify the parent context
   */
  @Override
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
  
  public void setErrorPages(ErrorPage[] errorPages)
  { 
    _errorPages=new ArrayList<ErrorPage>();
    for (ErrorPage errorPage:errorPages)
    { addErrorPage(errorPage);
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
  { addFilterMapping(new FilterMapping(urlPattern,name,true,false,false,false));
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
        if (name.endsWith("/"))
        { name=name.substring(0,name.length()-1);
        }
        _prefixServletNameMap.put
          (pattern,name);
      }
    }
  
  }
  
  public void setSecurityConstraints(SecurityConstraint[] securityConstraints)
  { this.securityConstraints=securityConstraints;
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

  @Override
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

  @Override
  public void setOutputGoverner(Governer governer)
  { _governer=governer;
  }

  @Override
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
  
  @Override
  public void setVirtualHostName(String hostName)
  { this.virtualHostName=hostName;
  }
  
  public void setExposeContainerFocus(boolean exposeContainerFocus)
  { this.exposeContainerFocus=exposeContainerFocus;
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
    
    if (_docRootDir!=null)
    {
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
    }
    else if (_docRootURI!=null)
    {
      if (!_docRootURI.isAbsolute())
      {
        if (_parentContext!=null)
        {
          setDocumentRootURI
            (_parentContext.getDocumentRootURI()
              .resolve(getDocumentRootURI())
            );
        }
        else
        { 
          throw new LifecycleException
            ("Root context cannot have a relative document root URI: "
            +_docRootURI
            );
        }
      }
    }
    
    log.info
      (getLogPrefix()+": Starting. root="+_docRootURI);

//    try
//    { _baseUrl=new URL("http",_hostName,_port,"/");
//    }
//   catch (MalformedURLException x)
//    { 
//      log.log(Level.ERROR,x.toString());
//     throw new RuntimeException(x.toString());
//    }
    
    if (_parentContext==null || _contextPath.equals(""))
    {
      if (_sessionManager==null)
      {
        SimpleHttpSessionManager sessionManager=new SimpleHttpSessionManager();
        sessionManager.setServletContext(this);
        sessionManager.setMaxInactiveInterval(_maxSessionInactiveInterval);
        if (_meter!=null)
        { sessionManager.installMeter(_meter.createChildMeter("sessions"));
        }
        sessionManager.init();
        _sessionManager=sessionManager;
        if (log.canLog(Level.CONFIG))
        { 
          log.log
            (Level.CONFIG
            ,"Sessions expire in "+_maxSessionInactiveInterval+" seconds"
            );
        }
      }
      if (_mimeMap==null)
      { 
        if (_parentContext==null)
        {
          // Load default mime map for server root context
          try
          { _mimeMap=new ExtensionMimeTypeMap("ExtensionMimeTypeMap.properties");
          }
          catch (IOException x)
          { log.log(Level.WARNING,"Error loading default mime type map: "+x.toString());
          }
        }
        
        try
        {
          if (_extensionMimeTypeMapResource!=null)
          {
            if (_mimeMap==null)
            { _mimeMap=new ExtensionMimeTypeMap();
            }
            _mimeMap.addFromResource(_extensionMimeTypeMapResource);
            if (log.canLog(Level.CONFIG))
            { 
              log.log
                (Level.CONFIG
                ,"Loaded additional mime types from "
                  +_extensionMimeTypeMapResource.getURI()
                );
            }
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
            +" serving "+_docRootURI
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

    if (_docRootURI!=null)
    { 
      loadWAR();
    }
    
    new PublicLocator(getHostName(),getStandardPort(),getSecurePort(),getContextPath())
      .set(this);
    
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
    if (_docRootURI==null)
    { return;
    }
    
    try
    {
      Resource docRoot=Resolver.getInstance().resolve(_docRootURI);
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
          if (holder.getFilter()
              .getClass().getName()
              .equals("spiralcraft.servlet.autofilter.Controller")
              )
          { 
            startedController=true;
            if (exposeContainerFocus)
            { 
              
              ((Controller) holder.getFilter()).bind(focus);
            }
          }
        }
        catch (ServletException x)
        { log.log(Level.WARNING,"Unexpected exception getting filter",x);
        }
        catch (ContextualException x)
        { log.log(Level.WARNING,"Error binding controller",x);
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

      @Override
      public String getFilterName()
      { return "Controller";
      }

      @Override
      public String getInitParameter(String arg0)
      { return null;
      }

      @Override
      public Enumeration<String> getInitParameterNames()
      { return null;
      }

      @Override
      public ServletContext getServletContext()
      { return SimpleHttpServiceContext.this;
      }
    };
    
    try
    { 
      controller.init(config);
      if (exposeContainerFocus)
      { controller.bind(focus);
      }
    }
    catch (ServletException x)
    { log.log(Level.WARNING,"Error starting controller",x);
    }
    catch (ContextualException x)
    { log.log(Level.WARNING,"Error binding controller",x);
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
      Resource docRoot=Resolver.getInstance().resolve(_docRootURI);
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
            if (libraryResources!=null)
            {
              for (Resource resource: libraryResources)
              { 
                Archive[] archives;
                
                try
                { 
                  archives=Archive.fromLibrary(resource);
                  if (archives.length==0)
                  { log("Library is empty: "+resource.getURI());
                  }
                  for (Archive archive: archives)
                  { contextClassLoader.addPrecedentArchive(archive);                  
                  }
                }
                catch (IOException x)
                { log("Failed to load archive "+resource.getURI(),x);
                }
              }
            }
            
            if (debugWAR)
            { contextClassLoader.setDebug(true);
            }
            contextClassLoader.start();
            this.contextClassLoader=contextClassLoader;
          }
          else
          {
            ServletURLClassLoader contextClassLoader
              =new ServletURLClassLoader(warRoot,libraryResources);
            this.contextClassLoader=contextClassLoader;
          }
        }
        else
        {
          log.log
            (Level.INFO,warRoot.getURI()+" does not exist "
              +", not loading WAR ClassLoader"
           );
        }
      }
      else
      { 
        log.log
          (Level.SEVERE,"Document root "
              +_docRootURI
              +" is not a valid directory"
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

  @Override
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

  @Override
  public Focus<?> bind(
    Focus<?> focus)
    throws BindException
  {
    this.focus=focus;
    return focus;
  }

  @Override
  public void setDeclarationInfo(
    DeclarationInfo declarationInfo)
  { this.declarationInfo=declarationInfo;
  }

  @Override
  public DeclarationInfo getDeclarationInfo()
  { return declarationInfo;
  }
  



}

class ServiceStatus
{
  HttpError error;
}

