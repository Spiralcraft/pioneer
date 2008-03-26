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
import java.io.PrintStream;
import java.io.OutputStreamWriter;
import java.io.IOException;


/**
 * Sends events to System.err
 */
public final class SystemErrStreamLog
  extends AbstractLog
{
  private Writer m_writer=null;
  private PrintStream _lastErr=System.err;
  private EventFormatter m_formatter=new DefaultEventFormatter();

  /**
   * Create a new StreamLog that will be configured later
   */
  public SystemErrStreamLog()
  { }

  /**
   * Write a message to the Log.
   */
  public final void log(int level,String message)
  {
    if (level<=this.level)
    { writeEvent(new Event(level,message,null));
    }
  }

  /**
   * Write an Event to the Log.
   */
  public final void logEvent(Event evt)
  {
    if (evt.getLevel()<=this.level)
    { writeEvent(evt);
    }
  }

  private final void writeEvent(Event evt)
  {
    if (m_writer==null || _lastErr!=System.err)
    { 
      m_writer=new BufferedWriter(new OutputStreamWriter(System.err));
      _lastErr=System.err;
    }

    try
    {
      m_formatter.format(m_writer,evt);
      m_writer.flush();
    }
    catch (IOException x)
    { }
  }
}
