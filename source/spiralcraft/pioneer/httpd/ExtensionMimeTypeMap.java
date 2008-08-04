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
/**
 * Represents a mapping of file extensions to Mime types.
 * 
 */
package spiralcraft.pioneer.httpd;

import java.io.InputStream;
import java.io.IOException;

import java.util.Properties;
import java.util.Enumeration;
import java.util.StringTokenizer;

import spiralcraft.text.CaseInsensitiveString;

import java.util.HashMap;

import spiralcraft.vfs.Resource;


public class ExtensionMimeTypeMap
{

  public ExtensionMimeTypeMap()
  { }

  /**
   * Loads the map from a resource 
   */
  public ExtensionMimeTypeMap(String resourceName)
    throws IOException
  {
    InputStream in=getClass().getResourceAsStream(resourceName);
    addFromInputStream(in);
    

  }

  public void addFromResource(Resource resource)
    throws IOException
  {
    InputStream in=resource.getInputStream();
    addFromInputStream(in);
  }

  public void addFromInputStream(InputStream in)
    throws IOException
  {
    if (in!=null)
    {
      Properties props=new Properties();
      props.load(in);
      in.close();
        
      Enumeration<?> enu=props.propertyNames();
      while (enu.hasMoreElements())
      {
        String mimeType=(String) enu.nextElement();
        String values=props.getProperty(mimeType);
        if (values!=null)
        { 
          StringTokenizer tok=new StringTokenizer(values,",");
          while (tok.hasMoreTokens())
          {
            String extension=tok.nextToken();
            if (extension.startsWith("."))
            { _map.put(new CaseInsensitiveString(extension.substring(1)),mimeType);
            }
            else
            { _map.put(new CaseInsensitiveString(extension),mimeType);
            }
          }
        }
      }
    }
    
  }

  public String get(String extension)
  { return _map.get(new CaseInsensitiveString(extension));
  }

  private HashMap<CaseInsensitiveString,String> _map
    =new HashMap<CaseInsensitiveString,String>();
  
}
