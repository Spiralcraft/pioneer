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
package spiralcraft.pioneer.io;

import java.io.File;

import java.beans.PropertyEditor;
import java.beans.PropertyEditorSupport;
import java.beans.PropertyEditorManager;

import java.util.StringTokenizer;

import java.util.LinkedList;

import java.security.AccessControlException;

/**
 * Encapsulates a cross platform filename, which
 *   is separated by '/'.
 */
public class Filename
{
  static
  { 
    try
    {
      PropertyEditorManager.registerEditor
        (Filename.class,_PropertyEditor.class);
    }
    catch (AccessControlException x)
    {
    }
  }

  public static class _PropertyEditor
    extends PropertyEditorSupport
  {
    public _PropertyEditor()
    {
    }

    public void setValue(Object value)
    { 
      _value=value;
      _text=((Filename) value).getPath();
    }
    
    public Object getValue()
    { return _value;
    }

    public String getAsText()
    { return _text;
    }

    public void setAsText(String text)
    { 
      _text=text;
      _value=new Filename(text);
    }

    private Object _value=null;
    private String _text=null;
    
  }

  public static final char separatorChar='/';

  public static final String separator="/";

  /**
   * Construct a filename from a path
   */
  public Filename(String name)
  {
    if (name.equals("/"))
    { _specifiedName="/";
    }
    else if (name.endsWith("/"))
    { _specifiedName=name.substring(0,name.length()-1);
    }
    else
    { _specifiedName=name;
    }
  }

  /**
   * Construct a Filename contained in a
   *   directory.
   */
  public Filename(Filename parentDir,String name)
  { this(parentDir==null?name:parentDir.toString()+"/"+name);
  }

  public Filename(String parentDir,String name)
  { this(parentDir==null?name:parentDir+"/"+name);
  }

  public Filename(Filename parentDir,Filename name)
  { this(parentDir==null?name.getPath():parentDir.toString()+"/"+name.getPath());
  }

  /**
   * Construct a Filename from a platform specific file
   */
  public Filename(File file)
  { this((file.isAbsolute()?"/":"")+file.getPath().replace(File.separatorChar,'/'));
  }

  /**
   * Return the path
   */
  public String toString()
  { return _specifiedName;
  }

  /**
   * Return the entire path.
   */
  public String getPath()
  { return _specifiedName;
  }

  public String getAbsolutePath()
  { 
    return new Filename
      (new File(localize().getAbsolutePath())
      ).getPath();
  }

  /** 
   * Return the parent directory part of the path.
   */
  public String getParent()
  { 
    if (_specifiedName.equals("/"))
    { return null;
    }
    int lastSlash=_specifiedName.lastIndexOf(separatorChar);
    if (lastSlash>0)
    { return _specifiedName.substring(0,lastSlash);
    }
    else if (lastSlash==0)
    { return "/";
    }
    else
    { return null;
    }
  }

  /**
   * Return the subdirectory part of the path (after the
   *   first directory)
   */
  public String getChildPath()
  {
    int startPos=_specifiedName.charAt(0)=='/'?1:0;
    int firstSlash=_specifiedName.indexOf(separatorChar, startPos);    
    if (firstSlash>-1 && _specifiedName.length()>1)
    { return _specifiedName.substring(firstSlash+1);
    }
    else
    { return null;
    }
  }

  /**
   * Returns the part of this filename that comes after
   *   the supplied firstPart. If the files are equal,
   *   an empty string is returned. If this filename
   *   does not begin with firstPart, null is returned.
   */
  public String subtract(Filename firstPart)
  {
    if (!getPath().startsWith(firstPart.getPath()))
    { return null;
    }
    if (getPath().length()==firstPart.getPath().length())
    { return "";
    }
    else
    { return getPath().substring(firstPart.getPath().length()+1);
    }
  }

  public Filename subtractFilename(Filename firstPart)
  {
    String name=subtract(firstPart);
    return name!=null?new Filename(name):null;
  }

  /**
   * Return the first subdirectory name in the path
   */
  public String getFirstName()
  { 
    if (_specifiedName.length()==0)
    { return null;
    }
    int startPos=_specifiedName.charAt(0)=='/'?1:0;
    int firstSlash=_specifiedName.indexOf(separatorChar,startPos);
    if (firstSlash>-1 && _specifiedName.length()>1)
    { return _specifiedName.substring(startPos,firstSlash);
    }
    else
    {
      if (startPos>0)
      { return _specifiedName.substring(startPos);
      }
      else
      { return _specifiedName;
      }
    }
  }

  /**
   * Return the filename part of the path
   */
  public String getName()
  {
    int lastSlash=_specifiedName.lastIndexOf(separatorChar);
    if (lastSlash>-1)
    { return _specifiedName.substring(lastSlash+1);
    }
    else
    { return _specifiedName;
    }    
  }

  /**
   * Return a platform specific File that points to 
   *   an equivalent path.
   */
  public File localize()
  { 
    if (isAbsolute() && _specifiedName.charAt(2)==':')
    { 
      return new File
        (_specifiedName.substring(1).replace('/',File.separatorChar));
    }
    else
    { return new File(_specifiedName.replace('/',File.separatorChar));
    }
  }

  /**
   * Implement Object.equals()
   */
  public boolean equals(Object obj)
  {
    return obj==this
      || (obj instanceof Filename &&
          _specifiedName.equals( ((Filename) obj).getPath())
         ); 
  }

  /**
   * Implement Object.hashCode() 
   */
  public int hashCode()
  { return _specifiedName.hashCode();
  }

  public boolean isAbsolute()
  { return _specifiedName.startsWith("/");
  }

  /**
   * Remove any relative elements in the path
   */
  public String collapseRelativeElements()
  {
    StringTokenizer tok=new StringTokenizer(_specifiedName,"/");
    LinkedList dirList=new LinkedList();
    while (tok.hasMoreTokens())
    {
      String dir=tok.nextToken();
      if (dir.equals("."))
      { continue;
      }
      else if (dir.equals(".."))
      {
        if (dirList.isEmpty())
        { return null;
        }
        else
        { dirList.removeLast();
        }
      }
      else
      { dirList.add(dir);
      }

    }        
    StringBuffer out=new StringBuffer();
    if (isAbsolute())
    { out.append("/");
    }

    while (!dirList.isEmpty())
    { 
      out.append((String) dirList.removeFirst());
      if (!dirList.isEmpty())
      { out.append("/");
      }
    }
    return out.toString();
  }    

  private String _specifiedName;


}
