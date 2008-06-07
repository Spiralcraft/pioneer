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

import java.util.List;
import java.util.ArrayList;

import spiralcraft.lang.AccessException;
import spiralcraft.lang.Channel;
import spiralcraft.lang.ChannelAdapter;
import spiralcraft.lang.Focus;
import spiralcraft.lang.Expression;
import spiralcraft.lang.BindException;
import spiralcraft.lang.Reflector;

import spiralcraft.lang.spi.BeanReflector;

import com.spiralcraft.data.lang.ValueContext;
import com.spiralcraft.data.lang.NameContext;
import com.spiralcraft.data.lang.MethodContext;

@SuppressWarnings("unchecked") // Legacy code will never use generics
public class ValueContextOptic
  extends ChannelAdapter
{
 
  private final ValueContext valueContext;
  
  public ValueContextOptic(ValueContext context)
  { this.valueContext=context;
  }
  
  public void setDebug(boolean debug)
  {
  }
  
  public Object get()
  { return this.valueContext.getValue();
  }
  
  public Channel resolve(Focus focus,String name,Expression[] parameters)
    throws BindException
  { 
    if (parameters==null)
    {
      if (this.valueContext instanceof NameContext)
      { 
        ValueContext context=
          ((NameContext) this.valueContext).getContextForName(name);
        if (context!=null)
        { return new ValueContextOptic(context);
        }  
      }
      return null;
    }

    if (this.valueContext instanceof MethodContext)
    {
      List<ChannelValueContext> params
        =new ArrayList<ChannelValueContext>(parameters.length);
      for (int i=0;i<parameters.length;i++)
      { params.add(new ChannelValueContext(focus.bind(parameters[i])));
      }

      ValueContext context=
        ((MethodContext) this.valueContext).getMethodForName(name,params);
      if (context!=null)
      { return new ValueContextOptic(context);
      }  
      
    }
    
    return null;    
  }  
  
  public Reflector getReflector()
  { 
    try
    {
      return BeanReflector.getInstance
        (this.valueContext.getValueClass
          (Thread.currentThread().getContextClassLoader()
          )
        );
    }
    catch (BindException x)
    { 
      x.printStackTrace(); 
      return null;
    }
  }


  @Override
  public void cache(Object key, Channel channel)
  {
    // TODO Auto-generated method stub
    
  }


  @Override
  public Channel getCached(Object key)
  {
    // TODO Auto-generated method stub
    return null;
  }


  @Override
  public boolean isWritable() throws AccessException
  {
    // TODO Auto-generated method stub
    return false;
  }
}
