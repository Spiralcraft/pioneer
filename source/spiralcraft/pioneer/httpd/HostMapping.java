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
 * Maps a path to a servlet
 */
public class HostMapping
{

  private HttpServiceContext context;
  private String[] hostNames;
  
  public void setHostNames(String[] hostNames)
  { this.hostNames=hostNames;
  }
  
  public String[] getHostNames()
  { return hostNames;
  }
  
  public void setContext(HttpServiceContext context)
  { this.context=context;
  }
  
  public HttpServiceContext getContext()
  { return context;
  }
  
  
}
