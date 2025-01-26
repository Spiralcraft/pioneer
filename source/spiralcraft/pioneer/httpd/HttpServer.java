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

import spiralcraft.net.ip.AddressSet;
import spiralcraft.pioneer.net.ConnectionHandlerFactory;
import spiralcraft.pioneer.net.ServerSocketFactory;
import spiralcraft.pioneer.net.ConnectionHandler;

import spiralcraft.service.AbstractService;
import spiralcraft.common.ContextualException;
import spiralcraft.common.LifecycleException;
import spiralcraft.lang.Focus;
import spiralcraft.lang.util.LangUtil;
import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import spiralcraft.pioneer.util.ThrowableUtil;


import java.net.Socket;
import java.net.URI;
import java.util.HashMap;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

import javax.servlet.ServletException;


import spiralcraft.meter.Meter;
import spiralcraft.meter.MeterContext;
import spiralcraft.meter.Register;
import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.Resource;

public class HttpServer
  extends AbstractService
  implements
    ConnectionHandlerFactory
{
  public static final String DEBUG_PROTOCOL
    ="spiralcraft.pioneer.httpd.protocol";
  
  public static final String DEBUG_IO
    ="spiralcraft.pioneer.httpd.io";

  public static final String DEBUG_SERVICE
    ="spiralcraft.pioneer.httpd.service";

  private ClassLog _log=ClassLog.getInstance(HttpServer.class);
  private HttpServiceContext _serviceContext;
  private HttpServerContext[] serverContexts;
  private int _socketTimeout=30000;
  private String _serverInfo;
  private int _initialBufferCapacity=8192;
  private Meter _meter;
  protected MeterContext meterContext;
  private Register _bytesOutputRegister;
  private Register _requestsRegister;
  private Register _connectionsRegister;
  private Register _activeConnectionsRegister;
  private Register _activeRequestsRegister;
  private Register _uncaughtIoExceptionsRegister;
  private Register _uncaughtRuntimeExceptionsRegister;
  private Register _uncaughtServletExceptionsRegister;
  private volatile int _connectionCount=0;  
  private boolean started;
  private boolean stopping;
  private Resource traceResource;
  private String _remoteAddressHeaderName;
  private AddressSet _serverProxyAddresses;
  private HashMap<String,HttpServerContext> hostMap=new HashMap<>();
  
  private DebugSettings debugSettings
    =new DebugSettings();
    
  
  protected Focus<?> focus;
  private volatile int nextContextId=0;
 
  public void setServerInfo(String serverInfo)
  { _serverInfo=serverInfo;
  }
  
  public void setTraceDir(URI uri)
    throws IOException
  { 
    Resource resource=Resolver.getInstance().resolve(uri);
    traceResource=resource;
    
  }
  
  public void setDebugProtocol(boolean val)
  { debugSettings.debugProtocol=val;
  }
  
  public void setDebugService(boolean val)
  { debugSettings.debugService=val;
  }
  
  public void setDebugIO(boolean val)
  { debugSettings.debugIO=val;
  }


  public void setDebugAPI(boolean val)
  { debugSettings.debugAPI=val;
  }

  public void setRemoteAddressHeaderName(String headerName)
  { this._remoteAddressHeaderName=headerName;
  }
  
  public String getRemoteAddressHeaderName()
  { return _remoteAddressHeaderName;
  }
  
  public AddressSet getServerProxyAddresses()
  { return _serverProxyAddresses;
  }
  
  public void setServerProxyAddresses(AddressSet addresses)
  { this._serverProxyAddresses=addresses;
  }
  
  boolean isProxy(byte[] ipAddress)
  {
    return _serverProxyAddresses!=null
       && _serverProxyAddresses.contains(ipAddress);
  }
  
  public void wroteBytes(int count)
  { 
    if (_meter!=null)
    { _bytesOutputRegister.adjustValue(count);
    }
    if (debugSettings.debugProtocol)
    { _log.fine("Wrote "+count+" bytes");
    }
  }
  
  public void requestStarted()
  { 
    if (_meter!=null)
    {
      _requestsRegister.incrementValue();
      _activeRequestsRegister.incrementValue();
    }
  }

  
  private void installMeter(MeterContext meterContext)
  {
    this.meterContext=meterContext;
    Meter meter=meterContext.meter("Service");
    _requestsRegister=meter.register("requests");
    _bytesOutputRegister=meter.register("bytesOutput");
    _connectionsRegister=meter.register("connections");
    _activeRequestsRegister=meter.register("activeRequests");
    _activeConnectionsRegister=meter.register("activeConnections");
    _uncaughtIoExceptionsRegister=meter.register("uncaughtIoExceptions");
    _uncaughtRuntimeExceptionsRegister=meter.register("uncaughtRuntimeExceptions");
    _uncaughtServletExceptionsRegister=meter.register("uncaughtServletExceptions");
    
  }

  /**
   * The root service context for a single-scope server
   * 
   * @param context
   */
  public void setServiceContext(HttpServiceContext context)
  { _serviceContext=context;
  }

  /**
   * The root service context for a single-scope server
   * 
   * @param context
   */
  public HttpServiceContext getServiceContext()
  { return _serviceContext;
  }
  
  /**
   * The set of independently scoped serverContexts running
   *   in this server
   * 
   * @param contexts
   */
  public void setServerContexts(HttpServerContext[] contexts)
  { this.serverContexts=contexts;
  }

  public HttpServerContext[] getServerContexts()
  { return this.serverContexts;
  }
  
  @Override
  public synchronized void start()
    throws LifecycleException
  {
    stopping=false;
    try
    {
      if (serverContexts==null)
      {
        if (_serviceContext==null)
        { 
          SimpleHttpServiceContext serviceContext=new SimpleHttpServiceContext();
          _serviceContext=serviceContext;
        }
        serverContexts=new HttpServerContext[] { _serviceContext };
      }
      else
      {
        if (_serviceContext!=null)
        { 
          throw new IllegalStateException
            ("Cannot have both a single ServiceContext AND ServerContexts");
        }
      }
      
      for (HttpServerContext ctx: serverContexts)
      { startServerContext(ctx);
      }
      if (_serverInfo==null)
      { _serverInfo="Spiralcraft HTTPD";
      }
      started=true;
      super.start();
    }
    finally
    { notifyAll();
    }
    
  }

  private void startServerContext(HttpServerContext ctx)
    throws LifecycleException
  {
    int id=nextContextId++;
    ctx.setDebugSettings(debugSettings);
    if (meterContext!=null)
    { 
      ctx.installMeter
        (meterContext.subcontext("context"+id));
    }
    try
    { ctx.bind(focus);
    }
    catch (ContextualException x)
    { throw new LifecycleException("Error binding ServletContext",x);
    }
    ctx.start();
    listContext(ctx);
  }



  @Override
  public synchronized void stop()
    throws LifecycleException
  { 
    stopping=true;
    super.stop();
    started=false;
    try
    {
      for (HttpServerContext ctx: serverContexts)
      { 
        delistContext(ctx);

        try
        { ctx.stop();
        }
        catch (Exception x)
        { log.log(Level.WARNING,"Error stopping "+ctx,x);
        }
      }      
    }
    finally
    { notifyAll();
    }
  }
  

  @Override
  public ConnectionHandler createConnectionHandler()
  { return new HttpConnectionHandler();
  }

  protected Focus<?> bindImports(Focus<?> focus)
    throws ContextualException
  {
    focus=super.bindImports(focus);
      
    MeterContext meterContext=LangUtil.findInstance(MeterContext.class, focus);
    if (meterContext!=null)
    { installMeter(meterContext.subcontext("httpd"));
    }
    return focus;
  }

  @Override
  protected Focus<?> bindExports(Focus<?> focus)
    throws ContextualException
  { 
    if (logLevel.isDebug())
    { log.debug("HttpServer binding to "+focus);
    }
    this.focus=focus;
    return focus;
  }

  /**
   * Maps the host header of the request to either a specific 
   *   context listed for the host or a catch-all context.
   *   
   * @param request
   * @return
   */
  private HttpServerContext mapHost(HttpServerRequest request)
  { 
    String host=request.getHeader("Host");
    if (host==null)
    { host="*";
    }
    else
    { host=host.toLowerCase();
    }
    HttpServerContext ret=hostMap.get(host);
    if (ret==null)
    { ret=hostMap.get("*");
    }
    return ret;
  }
  
  private void listContext(HttpServerContext ctx)
    throws LifecycleException
  {
    String[] hostNames=ctx.getHostNames();
    if (hostNames==null)
    { hostNames=new String[] {"*"};
    }
    for (String name: hostNames)
    { 
      if (hostMap.containsKey(name))
      { throw new LifecycleException("Host "+name+" already registered");
      }
      hostMap.put(name, ctx);
    }
  }
  
  private void delistContext(HttpServerContext ctx)
  {
    String[] hostNames=ctx.getHostNames();
    if (hostNames==null)
    { hostNames=new String[] {"*"};
    }
    for (String name: hostNames)
    { hostMap.remove(name,ctx);
    }
  }  

  class HttpConnectionHandler
    implements ConnectionHandler
  {

    private final HttpServerRequest _request
      =new HttpServerRequest(HttpServer.this,debugSettings);

    private final HttpServerResponse _response=new HttpServerResponse(_request,_initialBufferCapacity);
    { 
      _request.setResponse(_response);
      _response.setHttpServer(HttpServer.this);
      _response.setDebugSettings(debugSettings);
    }

    private boolean ensureRunning()
    {
      if (stopping)
      { return false;
      }
      
      if (!started)
      {
        // Make sure we're started before allowing connections
        synchronized (this)
        {
          if (stopping)
          { return false;
          }
          
          if (!started)
          { 
            try
            { wait();
            }
            catch (InterruptedException x)
            { return false;
            }
            
            if (!started)
            { return false;
            }
          }
            
        }
      }
      return true;
    }
    
    private OutputStream newTraceStream(int connectionNum)
      throws IOException
    {
      OutputStream traceStream=null;
      String traceFile="http"+connectionNum+".txt";
      if (traceResource!=null)
      { 
        if (!traceResource.exists())
        { 
          _log.log
            (Level.WARNING
              ,"Trace dir "+traceResource.getURI()+" does not exist"
            );
        }
        else if (traceResource.asContainer()==null)
        { 
          _log.log
            (Level.WARNING
              ,"Trace dir "+traceResource.getURI()+" is not a directory"
            );
        } 
        else
        {
          traceStream
            =traceResource.asContainer().getChild(traceFile)
            .getOutputStream();
        }
      }
      else
      { traceStream=new FileOutputStream(traceFile);
      }
      return traceStream;
    }
    
    @Override
    public void handleConnection(Socket socket,ServerSocketFactory factory)
    {
      if (!ensureRunning())
      { 
        try
        { socket.close();
        }
        catch (IOException x)
        { }
        return;
      }

      int connectionNum=_connectionCount++;
      
      if (_meter!=null)
      {
        _connectionsRegister.incrementValue();
        _activeConnectionsRegister.incrementValue();
      }

      OutputStream traceStream=null;

      try
      {
        if (debugSettings.debugIO)
        { traceStream=newTraceStream(connectionNum);
        }
        _request.setTraceStream(traceStream);
        _response.setTraceStream(traceStream);


        socket.setSoTimeout(_socketTimeout);
        factory.configureConnectedSocket(socket);

        if (debugSettings.debugProtocol)
        { 
          _log.log(Level.DEBUG,"Got HTTP connection from "
            +socket.getInetAddress().getHostAddress());
        }

        boolean done=false;
        while (!done)
        {
          try
          {
            try
            { _request.start(socket);
            }
            catch (InterruptedIOException x)
            { 
              if (debugSettings.debugProtocol)
              { 
                _log.log(Level.DEBUG
                        ,"HTTP connection timed out from "
                        +socket.getInetAddress().getHostAddress()
                        );
              }
              try
              { socket.close();
              }
              catch (Exception x2)
              { }
              done=true;
            }
          
            if (!done)
            {
              _response.start(socket,factory);
              _response.setHeader(HttpServerResponse.HDR_SERVER,_serverInfo);
            
              try
              { 
                _request.readHeaders();

                HttpServerContext context=mapHost(_request);
                if (context!=null)
                { context.service(_request,_response);
                }
                else
                { _response.sendError(503,"Service unavailable");
                }
              }
              catch (ServletException x)
              {
                if (_meter!=null)
                { _uncaughtServletExceptionsRegister.incrementValue();
                }
                _log.log(Level.SEVERE,"Servlet Exception: "+x.getMessage());
                _response.sendError(500,"Internal Server Error");
              }
              catch (RuntimeException x)
              {
                if (_meter!=null)
                { _uncaughtRuntimeExceptionsRegister.incrementValue();
                }
                _log.log(Level.SEVERE,"Runtime Exception: "+ThrowableUtil.getStackTrace(x));                
                _response.sendError(500,"Internal Server Error");
              }
              _request.finish();
              _response.finish();
              if (_response.shouldClose())
              { done=true;
              }
              else
              { socket.setSoTimeout(_response.getKeepaliveSeconds()*1000);
              }
            }
          }
          finally
          {
            if (_meter!=null && _request.wasStarted())
            { _activeRequestsRegister.decrementValue();
            }
            if (debugSettings.debugProtocol)
            { _log.log(Level.DEBUG,"Finished HTTP request from "+socket.getInetAddress().getHostAddress());
            }

          }
        }
      }
      catch (IOException x)
      { 
        if (_request.wasStarted())
        {
          if (_meter!=null)
          { _uncaughtIoExceptionsRegister.incrementValue();
          }
          // If request is not started, don't log anything because
          //    client closed keepalive connection, which is normal.
          String addr="(unknown)";
          try
          { 
            addr=socket.getInetAddress().getHostAddress();
            socket.close();
          }
          catch (IOException x2)
          { }
          if (_log.canLog(Level.INFO))
          { _log.log(Level.INFO,"IOException handling connection from "+addr+": "+x.toString()+": "+x.getCause());
          }
        }
      }
      finally
      { 
        if (_meter!=null)
        { _activeConnectionsRegister.decrementValue();
        }

        _request.cleanup();
        _response.cleanup();
        try
        {
          if (traceStream!=null)
          { traceStream.close();
          }
        }
        catch (Exception x)
        { }

        try
        {
          // 2009-02-11 mike: Fix FD leak
          if (!socket.isClosed())
          { socket.close();
          }
        }
        catch (IOException x)
        { 
          _log.log(Level.INFO,"Normal socket close threw Exception",x);
        }
        
        
        if (debugSettings.debugProtocol)
        { _log.log(Level.DEBUG,"Finished HTTP connection from "+socket.getInetAddress().getHostAddress());
        }

      }

    }
  }

}
