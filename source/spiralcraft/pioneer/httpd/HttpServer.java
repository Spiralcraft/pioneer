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

import spiralcraft.app.kit.AbstractComponent;
import spiralcraft.common.ContextualException;
import spiralcraft.common.LifecycleException;
import spiralcraft.lang.Focus;
import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import spiralcraft.pioneer.util.ThrowableUtil;


import java.net.Socket;
import java.net.URI;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

import javax.servlet.ServletException;


import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.Meterable;
import spiralcraft.vfs.Resolver;
import spiralcraft.vfs.Resource;

public class HttpServer
  extends AbstractComponent
  implements
    ConnectionHandlerFactory
    ,Meterable
{
  public static final String DEBUG_PROTOCOL
    ="spiralcraft.pioneer.httpd.protocol";
  
  public static final String DEBUG_IO
    ="spiralcraft.pioneer.httpd.io";

  public static final String DEBUG_SERVICE
    ="spiralcraft.pioneer.httpd.service";

  private ClassLog _log=ClassLog.getInstance(HttpServer.class);
  private HttpServiceContext _serviceContext;
  private int _socketTimeout=30000;
  private String _serverInfo;
  private int _initialBufferCapacity=8192;
  private Meter _meter;
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
  
  private boolean debugProtocol;
  private boolean debugService;
  private boolean debugIO;
  private boolean debugAPI;
  
  protected Focus<?> focus;
 
  public void setServerInfo(String serverInfo)
  { _serverInfo=serverInfo;
  }
  
  public void setDebugProtocol(boolean val)
  { debugProtocol=val;
  }
  
  public boolean getDebugProtocol()
  { return debugProtocol;
  }

  public void setDebugService(boolean val)
  { debugService=val;
  }
  
  public boolean getDebugService()
  { return debugService;
  }
  
  public void setDebugAPI(boolean debugAPI)
  { this.debugAPI=debugAPI;
  }
  
  public boolean getDebugAPI()
  { return debugAPI;
  }
  
  

  public void setDebugIO(boolean val)
  { debugIO=val;
  }

  public boolean getDebugIO()
  { return debugIO;
  }

  public void setTraceDir(URI uri)
    throws IOException
  { 
    Resource resource=Resolver.getInstance().resolve(uri);
    traceResource=resource;
    
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
    if (debugProtocol)
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

  @Override
  public void installMeter(Meter meter)
  {
    _meter=meter;
    _requestsRegister=_meter.createRegister(HttpServer.class,"requests");
    _bytesOutputRegister=_meter.createRegister(HttpServer.class,"bytesOutput");
    _connectionsRegister=_meter.createRegister(HttpServer.class,"connections");
    _activeRequestsRegister=_meter.createRegister(HttpServer.class,"activeRequests");
    _activeConnectionsRegister=_meter.createRegister(HttpServer.class,"activeConnections");
    _uncaughtIoExceptionsRegister=_meter.createRegister(HttpServer.class,"uncaughtIoExceptions");
    _uncaughtRuntimeExceptionsRegister=_meter.createRegister(HttpServer.class,"uncaughtRuntimeExceptions");
    _uncaughtServletExceptionsRegister=_meter.createRegister(HttpServer.class,"uncaughtServletExceptions");
    
  }

  public void setServiceContext(HttpServiceContext context)
  { _serviceContext=context;
  }

  public HttpServiceContext getServiceContext()
  { return _serviceContext;
  }
  
  @Override
  public synchronized void start()
    throws LifecycleException
  {
    stopping=false;
    try
    {
      if (_serviceContext==null)
      { 
        SimpleHttpServiceContext serviceContext=new SimpleHttpServiceContext();
        if (_meter!=null)
        { serviceContext.installMeter(_meter.createChildMeter("defaultContext"));
        }
        _serviceContext=serviceContext;
      }
      _serviceContext.setServer(this);
      try
      { _serviceContext.bind(focus);
      }
      catch (ContextualException x)
      { throw new LifecycleException("Error binding ServletContext",x);
      }
      _serviceContext.start();
    
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

  @Override
  public synchronized void stop()
    throws LifecycleException
  { 
    stopping=true;
    super.stop();
    started=false;
    try
    {
      if (_serviceContext==null)
      { _serviceContext.stop();
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


  @Override
  protected Focus<?> bindExports(Focus<?> focus)
  { 
    if (logLevel.isDebug())
    { log.debug("HttpServer binding to "+focus);
    }
    this.focus=focus;
    return focus;
  }


  class HttpConnectionHandler
    implements ConnectionHandler
  {

    private final HttpServerRequest _request
      =new HttpServerRequest(HttpServer.this);

    private final HttpServerResponse _response=new HttpServerResponse(_request,_initialBufferCapacity);
    { 
      _request.setResponse(_response);
      _response.setHttpServer(HttpServer.this);
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

        if (debugProtocol)
        { 
          _log.log(Level.DEBUG,"Got HTTP connection from "
            +socket.getInetAddress().getHostAddress());
        }

        
        if (debugIO)
        { traceStream=newTraceStream(connectionNum);
        }
        _request.setTraceStream(traceStream);
        _response.setTraceStream(traceStream);


        socket.setSoTimeout(_socketTimeout);
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
              if (debugProtocol)
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
                _serviceContext.service(_request,_response);
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
            if (debugProtocol)
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
        
        
        if (debugProtocol)
        { _log.log(Level.DEBUG,"Finished HTTP connection from "+socket.getInetAddress().getHostAddress());
        }

      }

    }
  }

}
