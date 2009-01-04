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

/**
 * Maps a path to a filter or servlet
 */
public class PatternMapping
{

  private String name;
  private String urlPattern;
  
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
  
}
