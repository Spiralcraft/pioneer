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
 * Handles connections by queuing them up for
 *   processing by a set of worker threads that
 *   use another ConnectionHandler to process
 *   results.
 *
 * The QueueConnectionHandler requires a
 *   ConnectionHandlerFactory in order to
 *   create new ConnectionHandlers that are
 *   pooled to process requests.
 *   
 */
package spiralcraft.pioneer.net;

import java.net.Socket;

import spiralcraft.pioneer.pool.ResourceFactory;
import spiralcraft.pioneer.pool.Pool;

import java.util.LinkedList;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.pioneer.util.ThrowableUtil;

import java.io.IOException;

import spiralcraft.pioneer.telemetry.Meterable;
import spiralcraft.pioneer.telemetry.Meter;
import spiralcraft.pioneer.telemetry.Register;
import spiralcraft.pioneer.telemetry.FrameListener;
import spiralcraft.pioneer.telemetry.FrameEvent;

public class QueueConnectionHandler
	implements ConnectionHandler,Meterable,FrameListener
{

	private ConnectionHandlerFactory _factory;
  private Pool _threadPool=new Pool();
  private final LinkedList<Socket> _queue
    =new LinkedList<Socket>();
  private final Object _queueMonitor=new Object();
  private final DispatchThread _dispatchThread=new DispatchThread();
//  private int _maxQueueLength=500;
  private Log _log=LogManager.getGlobalLog();
  private boolean _initialized=false;
  private boolean _finished=false;
  private Meter _meter;
  private Register _queueSizeRegister;
  private Register _connectsRegister;


  public QueueConnectionHandler()
  {
    _threadPool.setConserve(true);
    setInitialThreadCount(1);
    setHighWaterThreadCount(200);
    setLowWaterThreadCount(1);
  }

  public void installMeter(Meter meter)
  { 
    _meter=meter;
    _queueSizeRegister
      =_meter.createRegister
        (QueueConnectionHandler.class,"queueSize");
    _connectsRegister
      =_meter.createRegister
        (QueueConnectionHandler.class,"connects");
    _meter.setFrameListener(this);
    _threadPool.installMeter(_meter.createChildMeter("pool"));
  }

  public void nextFrame(FrameEvent event)
  { _queueSizeRegister.setValue(_queue.size());
  }

  public void init()
  {
    _threadPool.setResourceFactory(new ThreadFactory());
    _threadPool.init();
    _dispatchThread.start();
    _initialized=true;
  }

  public void stop()
  {
    _log.log(Log.INFO,"QueueConnectionHandler stopping");
    synchronized (_queueMonitor)
    {
      _finished=true;
      _threadPool.stop();
      _queueMonitor.notify();
    }
  }

	public void setConnectionHandlerFactory(ConnectionHandlerFactory fact)
	{ _factory=fact;
	}

  /**
   * Specify the number of threads that will be created before
   *   accepting requests.
   */
  public void setInitialThreadCount(int threads)
  { _threadPool.setInitialSize(threads);
  }

  /**
   * Specify the maximum number of threads to create in response
   *   to volume demand.
   */
  public void setHighWaterThreadCount(int threads)
  { _threadPool.setMaxSize(threads);
  }

  /**
   * Specify the point at which new threads will be created
   *   in response to volume demand.
   */
  public void setLowWaterThreadCount(int threads)
  { _threadPool.setMinAvailable(threads);
  }

  public void setDebug(boolean debug)
  { _threadPool.setDebug(debug);
  }

	public int getQueueSize()
	{ return _queue.size();
	}

	public int getNumOutstandingThreads()
	{ return _threadPool.getTotalSize()-_threadPool.getNumAvailable();
	}

	public int getNumAvailableThreads()
	{ return _threadPool.getNumAvailable();
	}

  /**
   * Implements ConnectionHandler.handleConnection()
   */
  public final void handleConnection(Socket sock)
  {
    
    assertInit();
    if (_finished)
    { 
      try
      { sock.close();
      }
      catch (IOException x)
      { }
    }
    else
    {
      if (_meter!=null)
      { _connectsRegister.incrementValue();
      }
      synchronized (_queueMonitor)
      { 
        _queue.add(sock);
        _queueMonitor.notify();
      }
    }
  }

  private static int _handlerCount=0;

  class DispatchThread
    extends Thread
  {
    public DispatchThread()
    { 
      super("ConnectionHandlerDispatch");
      setDaemon(true);
      setPriority(Thread.MAX_PRIORITY-1);
    }

    @Override
    public void run()
    {
      try
      {
        while (!_finished)
        {
          Socket sock=null;
          synchronized(_queueMonitor)
          {
            if (_queue.size()==0 && !_finished)
            { _queueMonitor.wait();
            }
            if (_queue.size()>0 && !_finished)
            { sock=_queue.removeFirst();
            }
          }
          if (sock!=null)
          {
            if (!_finished)
            {
              // Handle the connection
              ConnectionHandlerThread thread
                =(ConnectionHandlerThread) _threadPool.checkout();
        
              thread.handleConnection(sock);
            }
            else
            {
              // Ignore the request
              // Close the socket
              try
              { sock.close();
              }
              catch (IOException x)
              { }
            }
          }
        }

        // Clean up queue
        while (_queue.size()>0)
        { 
          // Ignore pending connections
          //   by closing sockets
          Socket sock= _queue.removeFirst();
          try
          { sock.close();
          }
          catch (IOException x)
          { }
        }
      }
      catch (InterruptedException x)
      {
      }
    }

    
  }

  class ThreadFactory
    implements ResourceFactory
  {
    public Object createResource()
    {
      ConnectionHandlerThread thread
        =new ConnectionHandlerThread(_factory.createConnectionHandler());
      thread.setDaemon(true);
      thread.start();

      return thread;
    }

    public void discardResource(Object resource)
    {
      ((ConnectionHandlerThread) resource).finish();
    }
  }

	class ConnectionHandlerThread
		extends Thread
	{
    private final ConnectionHandler _handler;
    private volatile Socket _socket;
    private boolean _done=false;
    private final Object _monitor=new Object();

		public ConnectionHandlerThread(ConnectionHandler handler)
		{ 
      super("ConnectionHandler"+_handlerCount++);
      _handler=handler;
      
		}

    public void finish()
    {
      synchronized (_monitor)
      {
        _done=true;
        _monitor.notify();
      }
    }

    public void handleConnection(Socket sock)
    {
      synchronized (_monitor) 
      {
        _socket=sock;
        _monitor.notify();
      }
    }

		@Override
    public void run()
		{
      try
      {
        while (!_done)
        {
          synchronized (_monitor)
          {
            if (!_done && _socket==null)
            { _monitor.wait();
            }

            if (_socket!=null)
            {
              try
              {
                _handler.handleConnection(_socket);
                _socket=null;
                _threadPool.checkin(this);
              }
              catch (Throwable x)
              { 
                _socket=null;
                _log.log(Log.SEVERE
                        ,"Uncaught exception- ConnectionHandler assumed unsalvagable\r\n"
                        +ThrowableUtil.getStackTrace(x)
                        );
                _threadPool.discard(this);
              }
            }
          }
        }
      }
      catch (InterruptedException x)
      { x.printStackTrace(); 
      }
		}


	}

  
  private final void assertInit()
  {
    if (!_initialized)
    { throw new RuntimeException("QueueConnectionHandler not initialized.");
    }
  }


}
