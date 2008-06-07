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

import spiralcraft.util.ByteBuffer;

import spiralcraft.pioneer.log.Log;
import spiralcraft.pioneer.log.LogManager;

import spiralcraft.time.Scheduler;
import spiralcraft.time.Clock;

import java.io.RandomAccessFile;

import spiralcraft.util.StringUtil;

import spiralcraft.pioneer.util.ThrowableUtil;

import java.util.Calendar;
import java.util.Date;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.io.File;

/**
 * A log that uses a rotating file
 */
public class RotatingFileAccessLog
  implements AccessLog,Runnable
{
  
  private static final byte[] CRLF="\r\n".getBytes();

  private String _filePrefix="access";
  private DateFormat _fileDateFormat=new SimpleDateFormat("-yyyy-MM-dd--HH-mm-ss");

  private RandomAccessFile _file;
  private AccessLogFormat _format=new ECLFAccessLogFormat();
  private ByteBuffer[] _buffers={new ByteBuffer(),new ByteBuffer()};
  private Object _mutex=new Object();
  private int _currentBuffer=0;
  private Log _log=LogManager.getGlobalLog();
  private long _flushIntervalMs=1000;
  private boolean _initialized=false;
  private long _maxLengthKB=16384;
  private File _directory=new File(System.getProperty("user.dir"));
  private Calendar calendar=Calendar.getInstance();
  private int day=calendar.get(Calendar.DAY_OF_YEAR);
  

  public void setFormat(AccessLogFormat format)
  { _format=format;
  }

  public final void log(final HttpServerRequest request,final HttpServerResponse response)
  { 
    assertInit();
    final byte[] bytes=StringUtil.asciiBytes(_format.format(request,response));
    synchronized (_mutex)
    {
      _buffers[_currentBuffer].append(bytes);
      _buffers[_currentBuffer].append(CRLF);
    }
  }

  public synchronized void start()
  {
    Scheduler.instance().scheduleIn
      (this
      ,_flushIntervalMs
      );
    _initialized=true;
    notify();
  }
  
  public synchronized void stop()
  {
    _initialized=false;
    notify();
  }

  public void setMaxLengthKB(long maxLengthKB)
  { _maxLengthKB=maxLengthKB;
  }

  public void setFilePrefix(String filePrefix)
  { _filePrefix=filePrefix;
  }

  public void setDirectory(File directory)
  { _directory=directory;
  }

  public void run()
  {
    try
    {
      if (!_initialized)
      { 
        if (_file!=null)
        { _file.close();
        }
        return;
      }
      
      if (_file==null)
      { 
        File targetFile=
          new File
            (_directory
            ,_filePrefix
            +".log"
            );

        if (targetFile.exists())
        { 
          calendar.setTime(new Date(targetFile.lastModified()));
          day=calendar.get(Calendar.DAY_OF_YEAR);
        }
        
        _file=new RandomAccessFile
          (targetFile
          ,"rw"
          );
      }
      if (_file.length()==0)
      {
        if (_file.length()==0)
        { 
          String header=_format.header();
          if (header!=null)
          { _file.write(StringUtil.asciiBytes(header+"\r\n"));
          }
        }
      }
      else
      { _file.skipBytes((int) _file.length());
      }


      int flushBuffer;
      synchronized (_mutex)
      { 
        // Swap buffers
        flushBuffer=_currentBuffer;
        _currentBuffer=(_currentBuffer==0?1:0);
      }
    
      _file.write(_buffers[flushBuffer].toByteArray());
      _buffers[flushBuffer].clear();

      if (dateChanged() || _file.length()>=_maxLengthKB*1024)
      {
        _file.getFD().sync();
        _file.close();
        new File(_directory,_filePrefix+".log")
          .renameTo(new File(_directory,_filePrefix+_fileDateFormat.format(new Date())+".log"));
        _file=null;
      }
      
      Scheduler.instance().scheduleIn
        (this
        ,_flushIntervalMs
        );
    }
    catch (Exception x)
    { 
      // Back off for a minute
      _log.log(Log.ERROR,"Error writing access log \r\n"+ThrowableUtil.getStackTrace(x));
      Scheduler.instance().scheduleIn
        (this
        ,60000
        );
    }

  }
  
  private boolean dateChanged()
  { 
    calendar.setTime(new Date(Clock.instance().approxTimeMillis()));
    int newDay=calendar.get(Calendar.DAY_OF_YEAR);
    if (newDay!=day)
    { 
      day=newDay;
      return true;
    }
    else
    { return false;
    }
  }

  private void assertInit()
  { 
    if (!_initialized)
    { 
      synchronized (this)
      { 
        if (!_initialized)
        { 
          try
          { wait();
          }
          catch (InterruptedException x)
          { throw new RuntimeException("Interrupted waiting for initialization");
          }
        }
      }
      if (!_initialized)
      { throw new RuntimeException("RotatingFileAccessLog has not been initialized");
      }
    }
  }

  
}
