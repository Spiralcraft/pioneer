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

import java.io.Writer;
import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;

import spiralcraft.vfs.Resource;

/**
 * Sends events to an OutputStream or a Writer
 */
public final class StreamLog
  extends AbstractLog
{
  private Writer m_writer=null;
  private EventFormatter m_formatter=new DefaultEventFormatter();
  
  /**
   * Create a new StreamLog that will be configured later
   */
  public StreamLog()
  { }

  /**
   * Create a new StreamLog that writes to the specified writer
   */
  public StreamLog(Writer wr)
  {
    if (wr instanceof BufferedWriter)
    { m_writer=wr;
    }
    else
    { m_writer=new BufferedWriter(wr);
    }
  }

  /**
   * Create a new StreamLog that writes to the specified
   *   OutputStream
   */
  public StreamLog(OutputStream os)
  { setOutputStream(os);
  }

  /**
   * Specify the Resource to which the Log will write
   */
  public void setResource(Resource resource)
    throws IOException
  { setOutputStream(resource.getOutputStream());
  }

  /**
   * Specify the OutputStream to which the Log will write
   */
  public void setOutputStream(OutputStream os)
  { m_writer=new BufferedWriter(new OutputStreamWriter(os));
  }

  /**
   * Write a message to the Log.
   */
  @Override
  public final void log(int level,String message)
  {
    if (level<=this.level)
    { writeEvent(new Event(level,message,null));
    }
  }

  /**
   * Write an Event to the Log.
   */
  @Override
  public final void logEvent(Event evt)
  {
    if (evt.getLevel()<=this.level)
    { writeEvent(evt);
    }
  }


  private final void writeEvent(Event evt)
  {
    if (m_writer!=null)
    {
      try
      {
        m_formatter.format(m_writer,evt);
        m_writer.flush();
      }
      catch (IOException x)
      { }
    }
  }
}
