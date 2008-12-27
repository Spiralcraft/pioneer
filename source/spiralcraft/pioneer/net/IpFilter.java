package spiralcraft.pioneer.net;


import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;

public class IpFilter
{

  private List<Subnet> _list=new ArrayList<Subnet>();
  
  /**
   * @param name
   */
  public void putContents(String name,Object value)
  { _list.add(new Subnet((String) value));
  }

  public boolean contains(byte[] address)
  { 
    Iterator<Subnet> it=_list.iterator();
    while (it.hasNext())
    { 
      Subnet subnet=it.next();
      if (subnet.contains(address))
      { return true;
      }
    }
    return false;
    
  }

}
