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
package spiralcraft.pioneer.log;

import spiralcraft.pioneer.io.RotatingLog;
import spiralcraft.pioneer.io.RotatingLogSource;

import spiralcraft.util.StringUtil;

import spiralcraft.util.SynchronizedQueue;

import java.util.Date;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.io.StringWriter;
import java.io.IOException;

import java.util.Map;
import java.util.HashMap;

public class RotatingFileLog
  extends RotatingLog
  implements RotatingLogSource
            ,Log
{

  private Map _debugProfile=new HashMap();

  public final void setDebugProfile(Map profile)
  { _debugProfile=profile;
  }

  public final Map getDebugProfile()
  { return _debugProfile;
  }

  public void setFilePrefix(String filename)
  { _filePrefix=filename;
  }

  public String getActiveFilename()
  { return _filePrefix+".log";
  }

  public String getNewArchiveFilename()
  { return _filePrefix+_fileDateFormat.format(new Date())+".log";
  }

  public byte[] getData()
  {
    try
    {
      StringBuffer out=new StringBuffer();
      while (_queue.getLength()>0)
      { out.append((String) _queue.next());
      }
      return StringUtil.asciiBytes(out.toString());
    }
    catch (InterruptedException x)
    { return new byte[0]; 
    }
  }

  public RotatingFileLog()
  { setSource(this);
  }

  public byte[] getHeader()
  { return null;
  }

  public boolean isLevel(int level)
  { return _level>=level;
  }

  public void setLevel(int level)
  { _level=level;
  }

  /**
   * Write a message to the Log.
   */
  public final void log(int level,String message)
  {
    if (level<=_level)
    { writeEvent(new Event(level,message,null));
    }
  }

  /**
   * Write an Event to the Log.
   */
  public final void logEvent(Event evt)
  {
    if (evt.getLevel()<=_level)
    { writeEvent(evt);
    }
  }

  /**
   * Return the current Log level (message filter threshold)
   */
  public final int getLevel()
  { return _level;
  }

  public final boolean isDebugEnabled(String debugGroupName)
  { return isLevel(DEBUG) && System.getProperty(debugGroupName)!=null;
  }

  private String _filePrefix;
  private SynchronizedQueue _queue
    =new SynchronizedQueue();
  private DateFormat _fileDateFormat
    =new SimpleDateFormat("-yyyy-MM-dd--HH-mm-ss-z");
  private int _level=Log.MESSAGE;
  private EventFormatter _formatter=new DefaultEventFormatter();

  private void writeEvent(Event event)
  {
    StringWriter stringWriter=new StringWriter();
    try
    { _formatter.format(stringWriter,event);
    }
    catch (IOException x)
    { }
    try
    { _queue.add(stringWriter.toString());
    }
    catch (InterruptedException x)
    { }
  }
}
