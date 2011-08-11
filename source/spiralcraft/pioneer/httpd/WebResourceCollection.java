//
// Copyright (c) 2011 Michael Toth
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

import spiralcraft.util.ArrayUtil;

/**
 * A declared Security Constraint for a ServletContext
 * 
 * @author mike
 *
 */ 
public class WebResourceCollection
  extends PatternMapping
{
    
  
  public String[] httpMethods;
  
  public void setHttpMethods(String[] httpMethods)
  { this.httpMethods=httpMethods;
  }
  
  public boolean matches(String httpMethod,String path)
  { 
    return (httpMethods==null 
            || ArrayUtil.contains(httpMethods,httpMethod)
           )
           &&
           matchesPattern(path)
           ;
  }
  
}
