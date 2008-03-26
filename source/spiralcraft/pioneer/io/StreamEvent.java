package spiralcraft.pioneer.io;

import java.io.InputStream;

public class StreamEvent
{
  private InputStream _inputStream;

  public StreamEvent()
  { }

  public StreamEvent(InputStream in)
  { setInputStream(in);
  }
   
  public InputStream getInputStream()
  { return _inputStream;
  }

  public void setInputStream(InputStream inputStream)
  { _inputStream=inputStream;
  }
}
