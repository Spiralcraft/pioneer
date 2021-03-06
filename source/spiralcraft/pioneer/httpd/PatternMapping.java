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
import spiralcraft.util.ArrayUtil;

/**
 * Maps a path to a filter or servlet
 */
public class PatternMapping
{
  public static boolean matchesPattern(String urlPattern,String uri)
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
    
    return matches;
    
  }  
  
  protected final ClassLog log=ClassLog.getInstance(getClass());
  private String name;
  private String[] urlPatterns;
  private boolean debug;
  
  
  public PatternMapping(String name,String urlPattern)
  { 
    this.name=name;
    addURLPattern(urlPattern);
  
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
  
  public void setDebug(boolean debug)
  { this.debug=debug;
  }
  
  public void setURLPattern(String urlPattern)
  { this.urlPatterns=new String[] {urlPattern};
  }
  
  public void addURLPattern(String urlPattern)
  { 
    if (this.urlPatterns==null)
    { this.urlPatterns=new String[] {urlPattern};
    }
    else
    { this.urlPatterns=ArrayUtil.append(urlPatterns,urlPattern);
    }
  }
  
  public void setURLPatterns(String[] urlPatterns)
  { this.urlPatterns=urlPatterns;
  }

  public String[] getURLPatterns()
  { return urlPatterns;
  }
  
  public boolean matchesPattern(String uri)
  {
    for (String pattern:urlPatterns)
    {
      if (matchesPattern(pattern,uri))
      { 
        if (debug)
        { log.fine("MATCH: "+pattern+" : "+uri);
        }
        return true;
      }
      else 
      {
        if (debug)
        { log.fine("NO MATCH: "+pattern+" : "+uri);
        }
      }

    }
    return false;
  }
    

}
