package spiralcraft.pioneer.io;

import java.util.ArrayList;
import java.util.Iterator;

public class StreamListenerSupport
  implements StreamListener
{

  private ArrayList _listeners=new ArrayList();

  public void add(StreamListener listener)
  { 
    if (!_listeners.contains(listener))
    { _listeners.add(listener);
    }
  }

  public void remove(StreamListener listener)
  { _listeners.remove(listener);
  }

  public void streamClosed(StreamEvent e)
  { 
    Iterator it=_listeners.iterator();
    while (it.hasNext())
    { ((StreamListener) it.next()).streamClosed(e);
    }
  }
}
