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

import spiralcraft.time.Clock;

import java.text.DateFormat;
import java.text.SimpleDateFormat;

import java.util.Date;
import java.net.InetAddress;

/**
 * Encapsulates an logged event
 */
public class Event
{
  private static String _localHostName="";
  private static String _localProcessName="";
  private static DateFormat DEFAULT_DATE_FORMAT
    =new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.S");
  private static char[] LEVELS = new char[] {'X','E','W','M','D'};

  static
  {
    try
    { _localHostName=InetAddress.getLocalHost().getHostName();
    }
    catch (Exception x)
    { }

    try
    {
      _localProcessName
        =System.getProperty("spiralcraft.process.name");
    }
    catch (SecurityException x)
    { x.printStackTrace();
    }
    if (_localProcessName==null)
    { 
      _localProcessName
        =Integer.toHexString
          ( (int) System.currentTimeMillis())
            .toUpperCase();
    }
  }

  private int m_level=0;
  private String m_message=null;
  private String m_threadName=null;
  private long m_time=0;
  private Object m_details=null;
  private String _processName=_localProcessName;
  private String _hostName=_localHostName;
  private String _instanceName="";
  private String _sourceClassName="";


  public Event(int level,String message,Object details)
  { 
    m_level=level;
    m_message=message;
    m_time=Clock.instance().approxTimeMillis();
    m_threadName=Thread.currentThread().getName();
    m_details=details;
  }

  public void setProcessName(String processName)
  { _processName=processName;
  }

  public void setHostName(String hostName) 
  { _hostName=hostName;
  }

  public void setInstanceName(String instanceName)
  { _instanceName=instanceName;
  }

  public String getInstanceName()
  { return _instanceName;
  }

  public void setSourceClassName(String className)
  { _sourceClassName=className;
  }

  public String getSourceClassName()
  { return _sourceClassName;
  }

  public int getLevel()
  { return m_level;
  }

  public String getMessage()
  { return m_message;
  }

  public String getThreadName()
  { return m_threadName;
  }

  public long getTime()
  { return m_time;
  }

  public Object getDetails()
  { return m_details;
  }

  public String getProcessName()
  { return _processName;
  }

  @Override
  public String toString()
  {
    final StringBuffer out=new StringBuffer();
    out.append("[");
    synchronized (DEFAULT_DATE_FORMAT)
    { out.append(DEFAULT_DATE_FORMAT.format(new Date(m_time)));
    }
    out.append("]:");
    out.append(LEVELS[m_level]);
    out.append(":");
    out.append(_hostName);
    out.append(":");
    out.append(_processName);
    out.append(":");
    out.append(m_threadName);
    out.append(":");
    if (_sourceClassName!=null && _sourceClassName.length()>0)
    {
      out.append("(");
      out.append(_sourceClassName);
      out.append(") ");
    }
    out.append(_instanceName);
    out.append(":");
    if (m_message!=null)
    {	out.append(m_message);
    }
    if (m_details!=null)
    {
      out.append("\r\n");
      out.append(m_details.toString());
    }
    return out.toString();
  }
}
