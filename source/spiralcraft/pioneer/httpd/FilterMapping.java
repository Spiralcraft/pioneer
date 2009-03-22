//
// Copyright (c) 2009,2009 Michael Toth
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

import spiralcraft.pioneer.httpd.AbstractHttpServletRequest.RequestSource;

public class FilterMapping
    extends PatternMapping
{

  private boolean onRequest=true;
  private boolean onInclude=false;
  private boolean onForward=false;
  private boolean global=false;
  
 
  public FilterMapping
    (String name
    ,String urlPattern
    ,boolean onRequest
    ,boolean onInclude
    ,boolean onForward
    )
  { super(name,urlPattern);
  }
  
  public FilterMapping()
  { super();
  }
  
  public boolean isOnRequest()
  { return onRequest;
  }
  
  public boolean isOnInclude()
  { return onInclude;
  }
  
  public boolean isOnForward()
  { return onForward;
  }
  
  public boolean isGlobal()
  { return global;
  }
  
  public void setOnRequest(boolean onRequest)
  { this.onRequest=onRequest;
  }

  public void setOnForward(boolean onForward)
  { this.onForward=onForward;
  }

  public void setOnInclude(boolean onInclude)
  { this.onInclude=onInclude;
  }
  
  public void setFilterName(String name)
  { setName(name);
  }
  
  /**
   * Whether the filter mapping will be inherited by child contexts
   * 
   * @param global
   */
  public void setGlobal(boolean global)
  { this.global=global;
  }
  
  public boolean matchesDispatch(RequestSource source)
  {
    switch (source)
    {
    case REQUEST:
      return onRequest;
    case INCLUDE:
      return onInclude;
    case FORWARD:
      return onForward;
    default:
      return false;
    }
  }
  
}
