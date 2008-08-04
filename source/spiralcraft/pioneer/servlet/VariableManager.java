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

package spiralcraft.pioneer.servlet;


import spiralcraft.text.html.URLDataEncoder;

import spiralcraft.vfs.StreamUtil;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;


import java.util.Iterator;
import java.util.HashMap;
import java.util.ArrayList;

import java.util.Map;

/**
 * Provides a simple, flexible and efficient way to
 *   manage servlet request variables. 
 */
public class VariableManager
{
	private HashMap<String,String[]> m_vars=null;

	public static HashMap<String,String[]> decodeURLEncoding(String encodedForm)
	{
	  HashMap<String,ArrayList<String>> buf
	    =new HashMap<String,ArrayList<String>>();
	  
    String[] pairs=encodedForm.split("&");
    for (String pair: pairs)
    {
      int eqpos=pair.indexOf('=');
      if (eqpos>0 && eqpos<pair.length()-1)
      {
        String name=URLDataEncoder.decode(pair.substring(0,eqpos));
        String valuesString=URLDataEncoder.decode(pair.substring(eqpos+1));
        
        ArrayList<String> values=buf.get(name);
        if (values==null)
        { 
          values=new ArrayList<String>();
          buf.put(name,values);
        }
        for (String value : valuesString.split(","))
        { values.add(value);
        }
      }

    }
    
    HashMap<String,String[]> ret=new HashMap<String,String[]>();
    for (Map.Entry<String,ArrayList<String>> entry: buf.entrySet())
    { 
      String[] values=new String[entry.getValue().size()];
      entry.getValue().toArray(values);
      ret.put(entry.getKey(),values);
    }
    return ret;
	}
	
  /**
   * Create a new VariableManager from the form submitted in an
   *   HTTP POST request.
   */
  public static VariableManager fromForm(HttpServletRequest request)
    throws IOException
  {
		if (request.getMethod().equals("POST"))
    {
      return VariableManager.fromStream
        (request.getIntHeader("Content-length")
        ,request.getInputStream()
        );
    }
    else
    { return new VariableManager(null);
    }

  }

  /**
   * Create a new VariableManager from the query string contained in
   *   the URL of an HTTP request.
   */
  public static VariableManager fromQuery(HttpServletRequest request)
  { 
    if (request.getQueryString()!=null)
    { 
      return new VariableManager
        (decodeURLEncoding(request.getQueryString()));
    }
    else
    { return new VariableManager(null);
    }
  }

  public static VariableManager fromStream(int len,InputStream in)
    throws IOException
  {
    String post=StreamUtil.readAsciiString(in, len);
    return new VariableManager(decodeURLEncoding(post));
  }
  
  /**
   * Create a variable manager from a
   *   Hashtable of String[]s
   */
	public VariableManager(HashMap<String,String[]> hash)
	{ 
	  if (hash!=null)
	  { m_vars=hash;
	  }
	  else
	  { m_vars=new HashMap<String,String[]>();
	  }
	}
			


  public VariableManager()
  { m_vars=new HashMap<String,String[]>();
  }

	/**
	 * Return the Dictionary that the
	 *   Variable manager interfaces to.
	 */
	public HashMap<String,?> getMap()
	{ return m_vars;
	}

	/**
	 * Return the the values for a variable.
	 */	
	public String[] getList(String name)
	{
		String[] var= m_vars.get(name);
		if (var==null)
		{ var=new String[0];
		}
		return var;
	}

	/**
	 * Return a single value for a variable.
	 */	
	public String getValue(String name)
	{
		String[] var=m_vars.get(name);
		String ret;
		if (var==null || var.length==0)
		{ ret=null;
		}
		else
		{ ret=var[0];
		}
		return ret;
	}

	/**
	 * Return a single value for a variable or
	 *   the default value if the variable was
	 *   not found.
	 */	
	public String getValue(String name,String deflt)
	{
		String ret=getValue(name);
		if (ret==null)
		{ return deflt;
		}
		else
		{ return ret;
		}
	}

  /**
   * Iterate through the names of the variables
   */
  public Iterator<String> getNames()
  { return m_vars.keySet().iterator();
  }

  /**
   * Change a value
   */
  public void setValue(String name,String value)
  {
    String[] valArray={value};
    m_vars.put(name,valArray);
  }

  /**
   * Change a value
   */
  public void setList(String name, String[] values)
  { m_vars.put(name,values);
  }

  /**
   * Remove a name/value pair from the collection
   */
  public void remove(String name)
  { m_vars.remove(name);
  }

  public boolean isEmpty()
  { return m_vars.isEmpty();
  }

  /**
   * Return a string encoded for inclusion in a link tag in an html document.
   */
  public String urlEncode()
  { 
    if (m_vars.size()==0)
    { return "";
    }
    StringBuffer out=new StringBuffer();
    for (String key: m_vars.keySet())
    {
      String[] values=m_vars.get(key);
      for (int i=0;i<values.length;i++)
      {
        if (out.length()>0)
        { out.append("&");
        }
        out.append(key);
        out.append("=");
        out.append(URLDataEncoder.encode(values[i]));
      }
    }
    return out.toString();
  }

  @Override
  public String toString()
  { 
    if (m_vars.size()==0)
    { return "[]";
    }
    StringBuffer out=new StringBuffer();
    out.append("[");
    boolean first=true;
    for (String key: m_vars.keySet())
    {
      if (first)
      { first=false;
      }
      else
      { out.append(",");
      }
      
      out.append(key);
      out.append("=");
      out.append("[");
      
      String[] values=m_vars.get(key);
      for (int i=0;i<values.length;i++)
      {
        out.append(values[i]);
        if (i<values.length-1)
        { out.append(",");
        }
      }
      out.append("]");
    }
    out.append("]");
    return out.toString();
  }

}
