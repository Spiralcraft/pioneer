//
// Copyright (c) 1998,2009 Michael Toth
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

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;

import java.util.Enumeration;
import java.util.Properties;


/**
 * Simple implementation of the ServletConfig interface
 */
public class SimpleServletConfig
  implements ServletConfig
{
  
  private ServletContext _context;
  private Properties _params=new Properties();
  private String _servletName;
  
  public SimpleServletConfig(String name,ServletContext context,Properties params)
  {
    _servletName=name;
    _context=context;
    _params=params;
  }

  public SimpleServletConfig(String name,ServletConfig config)
  { 
    _servletName=name;
    _context=config.getServletContext();
    Enumeration<?> e=config.getInitParameterNames();
    while (e.hasMoreElements())
    {
      String key=(String) e.nextElement();
      _params.put(key,config.getInitParameter(key));
    }
  }

  /** 
   * Return a shallow copy with a differet Servlet name
   */
  public SimpleServletConfig getClone(String name)
  { return new SimpleServletConfig(name,_context,_params);
  }

  @Override
  public ServletContext getServletContext()
  { return _context;
  }

  @Override
  public String getServletName()
  { return _servletName;
  }

  @Override
  public String getInitParameter(String name)
  { return _params.getProperty(name);
  }

  @Override
  public Enumeration<?> getInitParameterNames()
  { return _params.keys();
  }


  
}
