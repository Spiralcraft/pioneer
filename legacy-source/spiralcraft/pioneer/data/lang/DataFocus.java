//
// Copyright (c) 1998,2005 Michael Toth
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
package spiralcraft.pioneer.data.lang;

import spiralcraft.lang.SimpleFocus;
import spiralcraft.lang.Channel;

import com.spiralcraft.data.DataEnvironment;


import com.spiralcraft.data.lang.ValueContext;

import java.util.Map;

/**
 * Adapts legacy data.lang package to use new lang package
 */
public class DataFocus<T>
  extends SimpleFocus<T>
{ 
  
  private DataEnvironment dataEnvironment;
  private Map<String,ValueContext> contextProviders;
  private Channel<T> defaultOptic;
  
  public DataFocus()
  { 
    // XXX MUST FIX THIS: NEED TO GRAFT CHANNEL INTERFACE ONTO 
    //  BOTH contextProviders Map and dataEnvironment
    // setContext(new ContextImpl()); 
  }
  
  public DataFocus(DataFocus<?> parent,Channel<T> subject)
  { 
    this.dataEnvironment=parent.getDataEnvironment();
    this.contextProviders=parent.getContextProviders();
    this.defaultOptic=subject;
    setParentFocus(parent);
    
    // XXX MUST FIX THIS: NEED TO GRAFT CHANNEL INTERFACE ONTO 
    //  BOTH contextProviders Map and dataEnvironment
    // setContext(new ContextImpl());
  }
  
  public void setDataEnvironment(DataEnvironment val)
  { this.dataEnvironment=val;
  }

  public DataEnvironment getDataEnvironment()
  { return this.dataEnvironment;
  }
  
  public void setContextProviders(Map<String,ValueContext> val)
  { this.contextProviders=val;
  }
  
  public Map<String,ValueContext> getContextProviders()
  { return this.contextProviders;
  }

  @SuppressWarnings("unchecked") // Interfacing with non-generic system
  public void setDefaultContext(ValueContext val)
  { this.defaultOptic=new ValueContextOptic(val);
  }
  
  public Channel<T> getSubject()
  { return this.defaultOptic;
  }

   
  class ContextImpl
  {
    /**
     * Context.resolve(String name)
     */  
    public Channel<?> resolve(String name)
    { 
     
      if (dataEnvironment!=null)
      {
        ValueContext context=dataEnvironment.getContextForName(name);
        if (context!=null)
        { return new ValueContextOptic(context);
        }
      }
  
      if (contextProviders!=null)
      {
        ValueContext context=contextProviders.get(name);
        if (context!=null)
        { return new ValueContextOptic(context);
        }
      }
      return null;
    }
    
    public String[] getNames()
    { return null;
    }
    
  }
  
}
