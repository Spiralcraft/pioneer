package spiralcraft.pioneer.data.text;

import spiralcraft.lang.DefaultFocus;

import spiralcraft.data.DataEnvironment;
import spiralcraft.data.ContextProvider;

import spiralcraft.data.ValueContext;


public class DataFocus
  extends DefaultFocus
{ 
  
  private DataEnvironment dataEnvironment;
  private Map contextProviders;
  private ValueContext defaultContext;
  private Optic defaultOptic;
  
  public void setDataEnvironment(DataEnvironment val)
  { this.dataEnvironment=val;
  }
  
  public void setContextProviders(Map val)
  { this.contextProviders=val;
  }

  public void setDefaultContext(ValueContext val)
  { this.defaultContext=val;
  }
  
  public Optic getSubject()
  { return defaultOptic;
  }
  
  /**
   * Context.resolve(String name)
   */  
  public Optic resolve(String name)
  { 
    // Check defaultContext first
    // Check dataEnvironment and contextProviders
    // wrap result in a ValueContextOptic
    super.resolve(name);
  }
}
