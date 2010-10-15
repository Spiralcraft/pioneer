/**
 * Manages a rotating log file.
 */
package spiralcraft.pioneer.io;

import java.io.RandomAccessFile;
import java.io.File;

import spiralcraft.time.Scheduler;

import spiralcraft.pioneer.util.ThrowableUtil;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;


public class RotatingLog
  implements Runnable
{

  private static final ClassLog log=ClassLog.getInstance(RotatingLog.class);

  private RandomAccessFile _file;
  private long _pollIntervalMs=1000;
//  private boolean _initialized=false;
  private long _maxLengthKB=10000;
  private File _directory=new File(System.getProperty("user.dir"));
  private RotatingLogSource _source;
  private boolean _stopped=false;
  private boolean _suspended=false;

  public void startService()
  { 
    Scheduler.instance().scheduleNow(this);
//    _initialized=true;
  }

  public void stopService()
  { _stopped=true;
  }

  public void suspendService()
  { _suspended=true;
  }

  public void resumeService()
  { _suspended=false;
  }

  public void setSource(RotatingLogSource source)
  { _source=source;
  }

  public void setMaxLengthKB(long maxLengthKB)
  { _maxLengthKB=maxLengthKB;
  }

  public void setDirectory(File directory)
  { _directory=directory;
  }

  public void setPollIntervalMs(int pollIntervalMs)
  { _pollIntervalMs=pollIntervalMs;
  }

  @Override
  public void run()
  {
    try
    {
      if (_stopped)
      { return;
      }
      else if (_suspended)
      {
        Scheduler.instance().scheduleIn
          (this
          ,_pollIntervalMs
          );      
        return;
      }
      else
      {
        if (_file==null)
        { 
          File targetFile=
            new File
              (_directory
              ,_source.getActiveFilename()
              );
  
          _file=new RandomAccessFile
            (targetFile
            ,"rw"
            );
        }
  
        if (_file.length()==0)
        {
          if (_file.length()==0)
          { 
            byte[] header=_source.getHeader();
            if (header!=null)
            { _file.write(header);
            }
          }
        }
        else
        { _file.skipBytes((int) _file.length());
        }
  
  
        byte[] data=_source.getData();
        if (data!=null)
        {
          _file.write(data);
    
          if (_file.length()>=_maxLengthKB*1024)
          {
            _file.getFD().sync();
            _file.close();
    
            new File(_directory,_source.getActiveFilename())
              .renameTo(new File(_directory,_source.getNewArchiveFilename()));
    
            _file=null;
          } 
        }
        Scheduler.instance().scheduleIn(this,_pollIntervalMs);
      }
    }
    catch (Exception x)
    { 
      // Back off for a minute
      log.log(Level.SEVERE
              ,"Error writing rotating log '"
              +new File(_directory,_source.getActiveFilename())
              +"'\r\n"+ThrowableUtil.getStackTrace(x)
              );

      Scheduler.instance().scheduleIn
        (this
        ,60000
        );
    }

  }

//  private void assertInit()
//  { 
//    if (!_initialized)
//    { throw new RuntimeException("RotatingLog has not been initialized");
//    }
//  }


}  
  


