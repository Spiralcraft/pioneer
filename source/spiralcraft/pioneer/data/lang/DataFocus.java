package spiralcraft.pioneer.data.lang;

import spiralcraft.lang.DefaultFocus;
import spiralcraft.lang.Context;
import spiralcraft.lang.Optic;

import com.spiralcraft.data.DataEnvironment;
import com.spiralcraft.data.ContextProvider;

import com.spiralcraft.data.lang.ValueContext;

import java.util.Map;

/**
 * Adapts legacy data.lang package to use new lang package
 */
public class DataFocus
  extends DefaultFocus
{ 
  
  private DataEnvironment dataEnvironment;
  private Map contextProviders;
  private Optic defaultOptic;
  
  public DataFocus()
  { setContext(new ContextImpl()); 
  }
  
  public DataFocus(DataFocus parent,Optic subject)
  { 
    this.dataEnvironment=parent.getDataEnvironment();
    this.contextProviders=parent.getContextProviders();
    this.defaultOptic=subject;
    setParentFocus(parent);
    setContext(new ContextImpl());
  }
  
  public void setDataEnvironment(DataEnvironment val)
  { this.dataEnvironment=val;
  }

  public DataEnvironment getDataEnvironment()
  { return this.dataEnvironment;
  }
  
  public void setContextProviders(Map val)
  { this.contextProviders=val;
  }
  
  public Map getContextProviders()
  { return this.contextProviders;
  }

  public void setDefaultContext(ValueContext val)
  { this.defaultOptic=new ValueContextOptic(val);
  }
  
  public Optic getSubject()
  { return this.defaultOptic;
  }

   
  class ContextImpl
    implements Context
  {
    /**
     * Context.resolve(String name)
     */  
    public Optic resolve(String name)
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
        ValueContext context=(ValueContext) contextProviders.get(name);
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
