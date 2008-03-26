/**
 * A Runnable that continuously copies
 *   an InputStream into an OutputStream
 */
// 2000-10-18 mike
//
// Added 'alwaysBlock' to ensure that streams that don't report
//   'available' will always read a minimum of one byte.
//
// 2000-07-19 mike
// 
// Stopped defaults to false initially b/c join() may get called before stopped 
// can be reset in the run() method.
//   
// This means that we need to ensure that 'stopped' is set to false in
// the parent thread of the one that calls run(), or else another 'started'
// variable is used to prevent 'join()' from stopping prematurely.
//

package spiralcraft.pioneer.io;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;

import spiralcraft.pioneer.io.StreamListenerSupport;
import spiralcraft.pioneer.io.StreamListener;
import spiralcraft.pioneer.io.StreamEvent;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

public class StreamPump
  implements Runnable
{
  private static final String DEBUG_GROUP
    =StreamPump.class.getName();
  private static int ID=0;

  private int _id=ID++;
  private InputStream _in;
  private OutputStream _out;
  private IOException _exception;
  private int _bufsize=8192;
  private boolean _checkAvailable=false;
  private boolean _done;
  private boolean _stopped=false;
  private boolean _ignoreTimeouts=false;
  private int _pollIntervalMs=100;
  private boolean _closeStreams=true;
  private StreamListenerSupport _listeners
    =new StreamListenerSupport();
  private OutputStream _traceStream;
  private boolean _alwaysBlock;
  private boolean _draining;
  private Log _log=LogManager.getGlobalLog();

  public StreamPump(InputStream in,OutputStream out)
  {
    _in=in;
    _out=out;
  }

  public void setTraceStream(OutputStream trace)
  { _traceStream=trace;
  }

  public void setBufferSize(int bufsize)
  { _bufsize=bufsize;
  }
  

  /**
   * Specify the interval at which an inactive stream will
   *   be polled. Default=10ms
   */  
  public void setPollIntervalMs(int interval)
  { _pollIntervalMs=interval;
  }

  /** 
   * Indicate that the pump should poll the stream
   *   for available data instead of blocking to
   *   read full buffers.
   */
  public void setCheckAvailable(boolean check)
  { _checkAvailable=check;
  }

  /**
   * Close streams when stopping. Default is enabled.
   */
  public void setCloseStreams(boolean closeStreams)
  { _closeStreams=closeStreams;
  }

  public void drainAndJoin(int waitTime)
  { 
    _draining=true;
    join(waitTime);
  }

  public void stop()
  { 
    _done=true;
    synchronized (this)
    { notify();
    }
  }

  public void join(int waitTime)
  {
    try
    {
      synchronized (this)
      {
        if (!_stopped)
        { wait(waitTime);
        }
      }
    }
    catch (InterruptedException x)
    { }
    stop();
  }
  
  /**
   * Indicate that the pump should always try to
   *   read at least one byte from the stream, even
   *   when getAvailable() returns 0
   *
   * Note that on some implementations, close() will not close
   *   a blocked Socket stream. For this to work properly, make sure
   *   that any streams from Sockets have Socket.soTimeout set to 
   *   a small value (under 1 second), and also setIgnoreTimeouts(true).
   */
  public void setAlwaysBlock(boolean val)
  { _alwaysBlock=val;
  }

  /**
   * Indicate whether InterruptedIOExceptions
   *   should be ignored. Default is false. Set to
   *   true to allow streams to remain inactive longer than
   *   their timeout value.
   */
  public void setIgnoreTimeouts(boolean val)
  { _ignoreTimeouts=val;
  }

  public void run()
  {
    synchronized (this)
    { _stopped=false;
    }

    try
    { 
      byte[] buffer=new byte[_bufsize];
      while (!_done)
      {
        // Default to read the buffer size
        int bytesToRead=_bufsize;
        if (_checkAvailable)
        { 
          // Don't read more than what is available
          bytesToRead=Math.min(_in.available(),_bufsize);
        }
        else if (_draining)
        { 
          // Don't block if we're draining
          bytesToRead=0;
        }
        if (bytesToRead==0 && _alwaysBlock && !_draining)
        { 
          // If we always need to block and we aren't draining
          //   read at least 1 byte
          bytesToRead=1;
        }

        if (bytesToRead>0)
        {
          int read=0;
          try
          { 
            if (_log.isDebugEnabled(DEBUG_GROUP))
            { _log.log(Log.DEBUG,_id+":Reading "+bytesToRead);
            }
            read=_in.read(buffer,0,bytesToRead);
            if (_log.isDebugEnabled(DEBUG_GROUP))
            { _log.log(Log.DEBUG,_id+":Read "+read);
            }
          }
          catch (InterruptedIOException x)
          {
            if (!_ignoreTimeouts)
            { 
              if (_log.isDebugEnabled(DEBUG_GROUP))
              { _log.log(Log.DEBUG,_id+":Timed out");
              }
              throw x;
            }
          }
          if (read==-1)
          { break;
          }
          else if (read>0)
          { 
            if (_traceStream!=null)
            { 
              _traceStream.write(buffer,0,read);
              _traceStream.flush();
            }
            _out.write(buffer,0,read);
            _out.flush();
          }
        }
        else if (bytesToRead<0 || _draining)
        { break;
        }
        else
        {
          try
          {
            synchronized(this)
            { wait(_pollIntervalMs);
            }
          }
          catch (InterruptedException x)
          { break;
          }
        }
      }
    }
    catch (IOException x)
    { _exception=x;
    }
    finally
    {
      synchronized(this)
      {
        _stopped=true;
        if (_closeStreams)
        { 
          try
          { _in.close();
          }
          catch (IOException x)
          { }
          try
          { _out.close();
          }
          catch (IOException x)
          { }
        }
        _listeners.streamClosed(new StreamEvent(_in));
        notify();
      }
    }
  }

  public void addStreamListener(StreamListener listener)
  { _listeners.add(listener);
  }

  public void removeStreamListener(StreamListener listener)
  { _listeners.remove(listener);
  }

  public IOException getException()
  { return _exception;
  }

}
