package spiralcraft.pioneer.httpd;

public class DebugSettings
{
  boolean debugProtocol;
  boolean debugService;
  boolean debugIO;
  boolean debugAPI;

  public void setDebugProtocol(boolean val)
  { debugProtocol=val;
  }
  
  public boolean getDebugProtocol()
  { return debugProtocol;
  }

  public void setDebugService(boolean val)
  { debugService=val;
  }
  
  public boolean getDebugService()
  { return debugService;
  }
  
  public void setDebugAPI(boolean debugAPI)
  { this.debugAPI=debugAPI;
  }
  
  public boolean getDebugAPI()
  { return debugAPI;
  }
  
  public void setDebugIO(boolean val)
  { debugIO=val;
  }

  public boolean getDebugIO()
  { return debugIO;
  }
}
