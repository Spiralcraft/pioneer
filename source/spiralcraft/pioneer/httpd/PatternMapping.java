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

import spiralcraft.log.ClassLog;

/**
 * Maps a path to a filter or servlet
 */
public class PatternMapping
{
  protected final ClassLog log=ClassLog.getInstance(getClass());
  private String name;
  private String urlPattern;
  private boolean debug;
  
  
  public PatternMapping(String name,String urlPattern)
  { 
    this.name=name;
    this.urlPattern=urlPattern;
  
  }
  
  public PatternMapping()
  {
  }
  
  public void setName(String name)
  { this.name=name;
  }
  
  public String getName()
  { return name;
  }
  
  public void setURLPattern(String urlPattern)
  { this.urlPattern=urlPattern;
  }
  
  public String getURLPattern()
  { return urlPattern;
  }
  
    
  public boolean matchesPattern(String uri)
  {
    boolean matches=false;
    if (urlPattern.equals("/*"))
    { matches=true;
    }
    else if (urlPattern.startsWith("*") && 
              uri.endsWith(urlPattern.substring(1))
              )
    { matches=true;
    }
    else if (urlPattern.endsWith("/*") 
              && uri.startsWith(urlPattern.substring(0,urlPattern.length()-3))
            )
    { matches=true;
    }
    else if (uri.startsWith(urlPattern))
    { matches=true;
    }
    
    if (debug)
    { log.fine((matches?"MATCH":"NO-MATCH")+": "+urlPattern+" : "+uri);
    }
    return matches;
    
  }  
}
