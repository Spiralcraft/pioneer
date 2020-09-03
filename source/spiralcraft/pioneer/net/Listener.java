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
package spiralcraft.pioneer.net;

import java.io.IOException;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.UnknownHostException;


import spiralcraft.common.Lifecycle;
import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;
import spiralcraft.time.Clock;

import spiralcraft.pioneer.util.ThrowableUtil;


/**
 * Accepts connections on a specified host/port and delegates
 *   to a ConnectionHandler.
 *
 * If dynamic port assignment is required, set port to 0 and 
 *   set minPort to the minimum port of the dynamic range. Optionally
 *   set maxPort if the end of the range is less than 65535
 *
 * The Listener will only accept connections as fast as
 *   the ConnectionHandler can handle them. It is the 
 *   responsibility of the ConnectionHandler to provide connection
 *   management functionality such as thread pooling and
 *   request queueing.
 */
public class Listener
  implements Runnable,Lifecycle
{

  public static void test(String[] args)
    throws Exception
  {

    if (args.length>0)
    { 
      int port=8080;
      ServerSocketFactory factory=null;

      for (int i=0;i<args.length;i++)
      {
        if (args[i].equals("-port"))
        { port=Integer.parseInt(args[++i]);
        }
        else if (args[i].equals("-factory"))
        { 
          factory=(ServerSocketFactory) 
            Class.forName(args[++i]).getDeclaredConstructor().newInstance();
        }
      }

      Listener sd=new Listener();
      sd.setPort(port);
      if (factory!=null)
      { sd.setServerSocketFactory(factory);
      }
      sd.setConnectionHandler(new DebugConnectionHandler());
      sd.start();
      sd.join();
      

      System.out.println("stopped");

    }
  }

  private static final ClassLog log=ClassLog.getInstance(Listener.class);

  private boolean _finished=false;
  private int _listenBacklog=50;
  private int _timeoutMs=0;
  private int _connectionTimeout=0;
  private int _port=0;
  private InetAddress _addr=null;
  private String _interfaceName=null;
  private ServerSocket _serverSocket;
  private ShuntServerSocket _shuntServerSocket;
  private ConnectionHandler _handler=null;
  private boolean _shunt=false;
  private Thread _runner;
  private int _totalConnections = 0;
  private long _startTime;
  private IOException _bindException;
  private ServerSocketFactory _factory=new StandardServerSocketFactory();
  private int _minPort=0;
  private int _maxPort=65534;
  private int _boundPort=0;
  private boolean _tcpNoDelay=true;
  private boolean _debug;
  private boolean ignoreBindError;

  /**
   * Return the port that the socket is bound to
   */
  public int getBoundPort()
  { return _boundPort;
  }

  /**
   * Specify the port to listen to
   */
  public void setPort(int port)
  { _port=port;
  }

  /**
   * <p>Output messages to the log for many internal events.
   * </p>
   * @param debug
   */
  public void setDebug(boolean debug)
  { _debug=debug;
  }
  
  /**
   * Specify the minimum dynamic port to listen on.
   */ 
  public void setMinPort(int port)
  { _minPort=port;
  }

  /**
   * Specify the maximum dynamic port to listen on.
   */ 
  public void setMaxPort(int port)
  { _maxPort=port;
  }

  public void setServerSocketFactory(ServerSocketFactory factory)
  { _factory=factory;
  }
  
  /**
   * <p>If true, connections will be optimized for low latency when
   *   transmitting small chunks of data at the expense of an increased
   *   number of network packets. 
   * </p>
   * 
   * <p>Defaults to true
   * </p>
   * 
   * @param tcpNoDelay
   */
  public void setTcpNoDelay(boolean tcpNoDelay)
  { _tcpNoDelay=tcpNoDelay;
  }

  /**
   * Specify the interface to bind to. If
   *   not specified, all interfaces will be 
   *   bound.
   */
  public void setInterfaceName(String interfaceName)
  { _interfaceName=interfaceName;
  }

  /**
   * Indicate that a special in-process port
   *   should be used instead of a TCP/IP port.
   */
  public void setShunt(boolean shunt)
  { _shunt=shunt;
  }
  

  /** 
   * Indicate how many connections should be
   *   enqueued before new connections are
   *   refused.
   */
  public void setListenBacklog(int backlog)
  { _listenBacklog=backlog;
  }


  /**
   * Specify the maximum amount of time
   *   to wait for a connection
   */
  public void setSOTimeout(int ms)
  { _timeoutMs=ms;
  }

  /**
   * Specify the maximum amount of time a received
   *   connection will block on a read
   */
  public void setConnectionTimeout(int ms)
  { _connectionTimeout=ms;
  }

  /**
   * Provide a ConnectionHandler which does the actual
   *   work of handling incoming connections.
   */
  public void setConnectionHandler(ConnectionHandler handler)
  { _handler=handler;
  }


  public int getTotalConnections()
  { return _totalConnections;
  }

  public long getUptime()
  { return Clock.instance().approxTimeMillis()-_startTime;
  }

  /**
   * Keep operating 
   * @param ignoreBindError
   */
  public void setIgnoreBindError(boolean ignoreBindError)
  { this.ignoreBindError=ignoreBindError;
  }
  
  /**
   * Stop listening and close the server socket.
   */
  @Override
  public void stop()
  {
    _finished=true;
    stopListening();
  }

  /**
   * Start the Listener in a new daemon thread.
   */
  @Override
  public void start()
  {
    try
    {
      if (_interfaceName!=null)
      { _addr=InetAddress.getByName(_interfaceName);
      }
    }
    catch (UnknownHostException x)
    { throw new RuntimeException("Interface "+_interfaceName+" not found");
    }

    _runner=new Thread(this,"Listener-"+(_addr==null?"*:":_addr.getHostAddress()+":")+_port);
    _runner.setPriority(Thread.MAX_PRIORITY);
    _runner.setDaemon(true);
    _startTime = Clock.instance().approxTimeMillis();
    _runner.start();
    try
    { waitForBind();
    }
    catch (Exception x)
    { throw new RuntimeException(x.toString());
    }
  }

  public void waitForBind()
    throws IOException,InterruptedException
  {
    synchronized (this)
    {
      if (_serverSocket==null
          && _shuntServerSocket==null
          && _bindException==null
          )
      { wait();
      }
    }
    if (_bindException!=null)
    { 
      if (ignoreBindError)
      { 
        log.log(Level.WARNING
               ,"Ignoring error binding to "
               +(_interfaceName!=null?_interfaceName:"*")
               +":"
               +_port
               ,_bindException
               );
      }
      else
      { throw _bindException;
      }
    }
  }

  /**
   * Wait for the Listener to be stopped, if it has been
   *   started with start()
   */
  public void join()
  {
    try
    { _runner.join();
    }
    catch (InterruptedException x)
    { }
  }

  /**
   * Handle incoming connections
   */
  @Override
  public void run()
  {
    stopListening();

    _boundPort=_port;
    synchronized (this)
    {
      if (_addr!=null)
      {
        try
        {
          if (_shunt)
          { _shuntServerSocket=new ShuntServerSocket(_boundPort);
          }
          else
          {
            if (_boundPort==0 && _minPort>0)
            { _boundPort=_minPort;
            }
            while (true)
            {
              try
              { _serverSocket=_factory.createServerSocket(_boundPort,_listenBacklog,_addr);
              }
              catch (BindException x)
              { 
                if (_minPort==0 || _boundPort==_maxPort)
                { throw x;
                }
                else
                { _boundPort++;
                }
                continue;
              }
              break;
            }
            if (log.canLog(Level.INFO))
            {
              log.log
              (Level.INFO
              ,"Listening on "
                +_serverSocket.getInetAddress().getHostAddress()
                +":"+_serverSocket.getLocalPort()
              );
            }
          }
        }
        catch (IOException x)
        { 
          _bindException=x;
          log.log(Level.INFO,"Binding socket to "+_addr.getHostAddress()+":"+_boundPort+" "+x.toString());
        }
      }
      else
      {
        try
        {
          if (_shunt)
          { _shuntServerSocket=new ShuntServerSocket(_boundPort);
          }
          else
          {
            if (_boundPort==0 && _minPort>0)
            { _boundPort=_minPort;
            }
            while (true)
            {
              try
              { _serverSocket=_factory.createServerSocket(_boundPort,_listenBacklog);
              }
              catch (BindException x)
              {
                if (_minPort==0 || _boundPort==_maxPort)
                { throw x;
                }
                else
                { _boundPort++;
                }
                continue;
              }
              break;
            }
            if (log.canLog(Level.INFO))
            {
              log.log
              (Level.INFO
              ,"Listening on "
                +_serverSocket.getInetAddress().getHostAddress()
                +":"+_serverSocket.getLocalPort()
              );
            } 
  
          }
        }
        catch (IOException x)
        { 
          _bindException=x;
          log.log(Level.SEVERE,"Binding socket to port "+_boundPort+" "+x.toString());
        }
      }
  
      notify();
    }

    if (_serverSocket!=null && _timeoutMs>0)
    {
      try
      { _serverSocket.setSoTimeout(_timeoutMs);
      }
      catch (SocketException x)
      { log.log(Level.SEVERE,"Setting SoTimout for "+_serverSocket+" "+x.toString());
      }
    }

    if (_handler==null)
    { throw new RuntimeException("No ConnectionHandler set-up for "+getSocketDescription());
    }

    while (!_finished && (_serverSocket!=null || _shuntServerSocket!=null))
    {
      Socket sock=null;
      try
      {
        sock=_shunt?_shuntServerSocket.accept():_serverSocket.accept();

        if ( sock!=null)
        {
          _totalConnections++;

          if (_debug && log.canLog(Level.DEBUG))
          { log.log(Level.INFO,"Got connection from "+sock.getInetAddress().getHostAddress());
          }
          try
          {
            sock.setTcpNoDelay(_tcpNoDelay);
            if (_connectionTimeout>0)
            { sock.setSoTimeout(_connectionTimeout);
            }
            _handler.handleConnection(sock,_factory);
          }
          catch (Throwable e)
          {
            log.log(Level.SEVERE
                    ,"Uncaught exception accepting incoming connection on "
                      +getSocketDescription()
                      +": "
                      +ThrowableUtil.getStackTrace(e)
                    );
          }
        }
        else
        {
          if (log.canLog(Level.DEBUG))
          { log.log(Level.INFO,"accept() returned null");
          }
        }
      }
      catch (IOException e)
      {
        if (!_finished 
          && !(e instanceof SocketException) 
          && !e.getMessage().equals("Socket closed")
          )
        {
          log.log(Level.SEVERE
                    ,"IOException accepting incoming connection on "
                        +getSocketDescription()
                        +": "
                        +e.toString()
                    );
        }
        else
        { log.info("Server socket closed: "+getSocketDescription());
        }
      }

    }

  }

  public int getListenPort()
  { return _serverSocket.getLocalPort();
  }

  /**
   * Stop listening
   */
  private void stopListening()
  {
    try
    {
      if (_shunt)
      { _shuntServerSocket.close();
      }
      else
      { _serverSocket.close();
      }
    }
    catch (Exception x)
    { }
  }

  private String getSocketDescription()
  { return _shunt?_shuntServerSocket.toString():_serverSocket.toString();
  }
}
