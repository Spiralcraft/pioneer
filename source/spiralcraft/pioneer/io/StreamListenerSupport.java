package spiralcraft.pioneer.io;

import java.util.ArrayList;
import java.util.Iterator;

public class StreamListenerSupport
  implements StreamListener
{

  private ArrayList<StreamListener> _listeners
    =new ArrayList<StreamListener>();

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
    Iterator<StreamListener> it=_listeners.iterator();
    while (it.hasNext())
    { it.next().streamClosed(e);
    }
  }
}
