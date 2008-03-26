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

import java.util.Map;
import java.util.HashMap;

public abstract class AbstractLog
  implements Log
{
  
  protected int level=Log.WARNING;

  public abstract void log(int level,String message);

  public abstract void logEvent(Event evt);

  private Map _debugProfile=new HashMap();

  public final Map getDebugProfile()
  { return _debugProfile;
  }
  
  public final void setDebugProfile(Map profile)
  { _debugProfile=profile;
  }

  /**
   * Return the current Log level (message filter threshold)
   */
  public final int getLevel()
  { return level;
  }

  /**
   * Specify the Log level (message filter threshold)
   */
  public void setLevel(int level)
  { this.level=level;
  }

  /**
   * Return whether messages of the specified level will
   *   be accepted.
   */
  public final boolean isLevel(int lvl)
  { return lvl<=level;
  }

  public final boolean isDebugEnabled(String debugGroupName)
  { 
    if (!isLevel(DEBUG))
    { return false;
    }
    String debugProfile=(String) _debugProfile.get(debugGroupName);
    return debugProfile!=null && debugProfile.equals("true");
  }

}


