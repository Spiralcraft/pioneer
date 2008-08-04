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


/**
 * Manages Logs
 */
public class LogManager
{

  private static Log m_globalLog=new SystemErrStreamLog();

  /**
   * Obtain the application-wide log. Never returns null.
   */
  public static Log getGlobalLog()
  { return m_globalLog;
  }

  /**
   * Specify the application-wide log.
   */
  public static void setGlobalLog(Log log)
  {
    if (log==null)
    { m_globalLog=new NullLog();
    }
    else
    { m_globalLog=log;
    }
  }

}
