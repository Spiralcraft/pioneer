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

import spiralcraft.pioneer.net.ConnectionHandlerFactory;
import spiralcraft.pioneer.net.ConnectionHandler;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.pioneer.util.ThrowableUtil;


import java.net.Socket;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.FileOutputStream;

import javax.servlet.ServletException;


import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.Meterable;

public class HttpServer
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

  private static final String version="1.0pre1";

  private Log _log=LogManager.getGlobalLog();
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
  private int _connectionCount=0;


  public void setServerInfo(String serverInfo)
  { _serverInfo=serverInfo;
  }
  
  public void wroteBytes(int count)
  { 
    if (_meter!=null)
    { _bytesOutputRegister.adjustValue(count);
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
  
  public void startService()
  {
    if (_serviceContext==null)
    { 
      SimpleHttpServiceContext serviceContext=new SimpleHttpServiceContext();
      if (_meter!=null)
      { serviceContext.installMeter(_meter.createChildMeter("defaultContext"));
      }
      serviceContext.init();
      _serviceContext=serviceContext;
    }
    
    if (_serverInfo==null)
    {
      if (version!=null)
      { _serverInfo="Spiralcraft Web Server v"+version;
      }
      else
      { _serverInfo="Spiralcraft Web Server";
      }
    }
  }

  public void stopService()
  {
  }

  public ConnectionHandler createConnectionHandler()
  { return new HttpConnectionHandler();
  }



  class HttpConnectionHandler
    implements ConnectionHandler
  {

    private final HttpServerRequest _request=new HttpServerRequest();
    { _request.setHttpServer(HttpServer.this);
    }

    private final HttpServerResponse _response=new HttpServerResponse(_request,_initialBufferCapacity);
    { 
      _request.setResponse(_response);
      _response.setHttpServer(HttpServer.this);
    }

    public void handleConnection(Socket socket)
    {
      if (_meter!=null)
      {
        _connectionCount++;
        _connectionsRegister.incrementValue();
        _activeConnectionsRegister.incrementValue();
      }

      OutputStream traceStream=null;

      try
      {

        if (_log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
        { _log.log(Log.DEBUG,"Got HTTP connection from "+socket.getInetAddress().getHostAddress());
        }

        if (_log.isDebugEnabled(HttpServer.DEBUG_IO))
        { traceStream=new FileOutputStream("http"+_connectionCount+".txt");
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
              if (_log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
              { 
                _log.log(Log.DEBUG
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
              _response.start(socket);
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
                _response.sendError(500,"Internal Server Error");
                _log.log(Log.ERROR,"Servlet Exception: "+x.getMessage());
              }
              catch (RuntimeException x)
              {
                if (_meter!=null)
                { _uncaughtRuntimeExceptionsRegister.incrementValue();
                }
                _response.sendError(500,"Internal Server Error");
                _log.log(Log.ERROR,"Runtime Exception: "+ThrowableUtil.getStackTrace(x));
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
            if (_log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
            { _log.log(Log.DEBUG,"Finished HTTP request from "+socket.getInetAddress().getHostAddress());
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
          if (_log.isLevel(Log.MESSAGE))
          { _log.log(Log.MESSAGE,"IOException handling connection from "+addr+": "+x.toString());
          }
        }
      }
      finally
      { 
        if (_meter!=null)
        { _activeConnectionsRegister.decrementValue();
        }

        _request.cleanup();
        try
        {
          if (traceStream!=null)
          { traceStream.close();
          }
        }
        catch (Exception x)
        { }

        if (_log.isDebugEnabled(HttpServer.DEBUG_PROTOCOL))
        { _log.log(Log.DEBUG,"Finished HTTP connection from "+socket.getInetAddress().getHostAddress());
        }

      }

    }
  }

}