package spiralcraft.pioneer.data.lang;

import spiralcraft.lang.DefaultFocus;
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
  { }
  
  public DataFocus(DataFocus parent,Optic subject)
  { 
    this.dataEnvironment=parent.getDataEnvironment();
    this.contextProviders=parent.getContextProviders();
    this.defaultOptic=subject;
    setParentFocus(parent);
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
  
  /**
   * Context.resolve(String name)
   */  
  public Optic resolve(String name)
  { 
   
    if (this.dataEnvironment!=null)
    {
      ValueContext context=this.dataEnvironment.getContextForName(name);
      if (context!=null)
      { return new ValueContextOptic(context);
      }
    }

    if (this.contextProviders!=null)
    {
      ValueContext context=(ValueContext) this.contextProviders.get(name);
      if (context!=null)
      { return new ValueContextOptic(context);
      }
    }
    
    return super.resolve(name);
  }
}
