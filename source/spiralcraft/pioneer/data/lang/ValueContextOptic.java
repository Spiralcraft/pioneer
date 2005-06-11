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

import spiralcraft.lang.Optic;
import spiralcraft.lang.OpticAdapter;
import spiralcraft.lang.Focus;
import spiralcraft.lang.Expression;
import spiralcraft.lang.BindException;
import spiralcraft.lang.OpticFactory;

import spiralcraft.lang.optics.Prism;

import com.spiralcraft.data.lang.ValueContext;
import com.spiralcraft.data.lang.NameContext;
import com.spiralcraft.data.lang.MethodContext;

public class ValueContextOptic
  extends OpticAdapter
{
 
  private final ValueContext valueContext;
  
  public ValueContextOptic(ValueContext context)
  { this.valueContext=context;
  }
  
  
  public Object get()
  { return this.valueContext.getValue();
  }
  
  public Optic resolve(Focus focus,String name,Expression[] parameters)
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
      List params=new ArrayList(parameters.length);
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
  
  public Prism getPrism()
  { 
    try
    {
      return OpticFactory.getInstance().findPrism
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
}
