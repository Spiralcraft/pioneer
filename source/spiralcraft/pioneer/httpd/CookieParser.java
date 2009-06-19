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

import java.util.List;
import java.util.ArrayList;

import javax.servlet.http.Cookie;

import java.text.ParseException;

/**
 *
 * From RFC 2109
 *
 *  cookie          =       "Cookie:" cookie-version
 *                          1*((";" | ",") cookie-value)
 *  cookie-value    =       NAME "=" VALUE [";" path] [";" domain]
 *  cookie-version  =       "$Version" "=" value
 *  NAME            =       attr
 *  VALUE           =       value
 *  path            =       "$Path" "=" value
 *  domain          =       "$Domain" "=" value
 */  
public class CookieParser
{
  
  private List<Cookie> result=new ArrayList<Cookie>();
  private Cookie cookie;
  private int pos;
  private final String header;
//  private String path;
//  private String domain;
  private Integer version;
  
  public CookieParser(String header)
  { this.header=header;
  }
  
  public List<Cookie> parse()
    throws ParseException
  {
    readHeader();
    return result;
  }
  
  private void readHeader()
    throws ParseException
  { 
    if (pos==header.length())
    { return;
    }
    
    // Version prefix
    version=null;
    
    if (header.charAt(pos)=='$')
    {
      String versionAttr=readUntil('=');
      if (!"$version".equalsIgnoreCase(versionAttr))
      { throw new ParseException
          ("Cookie: Expected '$version', found '"+versionAttr+"'",pos);
      }
      version=Integer.parseInt(readUntil(';').trim());
    }
        
    while (pos<header.length())
    { 
      readPair();
    }
    if (cookie!=null)
    { 
      // Deal with last one
      result.add(cookie);
    }
    
    
    
  }
  
  private void readPair()
    throws ParseException
  {
    
    String attr=readUntil('=');
    if (attr==null)
    { throw new ParseException("Cookie: No attribute found",pos);
    }
    
    // Discard valueless attributes
    String[] otherAttrs=attr.split(";");
    attr=otherAttrs[otherAttrs.length-1];
    attr=attr.trim();
    
    String valspec=readUntil(';');
    if (valspec==null)
    { valspec="";
    }
    valspec=valspec.trim();

    if (attr.equalsIgnoreCase("$path"))
    { 
      // Path for last read cookie
      if (cookie==null)
      { throw new ParseException("Cookie: No pair to set path for",pos);
      }
      cookie.setPath(valspec);
    }
    else if (attr.equalsIgnoreCase("$domain"))
    { 
      // Domain for last read cookie
      if (cookie==null)
      { throw new ParseException("Cookie: No pair to set domain for",pos);
      }
      cookie.setDomain(valspec);
    }
    else
    {
      // New attr-value pair so new cookie
      if (cookie!=null)
      { result.add(cookie);
      }
      cookie=new Cookie(attr,valspec);
      if (version!=null)
      { cookie.setVersion(version);
      }
    }

  }
  
  private String readUntil(char chr)
  {
    if (pos==header.length())
    { return null;
    }
    
    String ret=null;
    int toPos=header.indexOf(chr,pos);
    if (toPos>0)
    { 
      ret=header.substring(pos,toPos);
      pos=toPos+1;
    }
    else
    { 
      ret=header.substring(pos,header.length());
      pos=header.length();
    }
    if (ret.length()==0)
    { return "";
    }
    if (ret.charAt(0)=='"')
    {
      if (ret.charAt(ret.length()-1)=='"')
      { ret=ret.substring(1,ret.length()-1);
      }
      else
      { 
        ret=ret.substring(1).concat(new Character(chr).toString());
        ret=ret.concat(readUntil('"'));
        pos=Math.min(pos+1,header.length()); // Skip assumed ';' after quote
      }
    }
    return ret;
  }
}