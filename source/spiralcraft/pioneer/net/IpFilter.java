package spiralcraft.pioneer.net;


import java.net.InetAddress;
import java.util.List;
import java.util.ArrayList;

import java.util.Iterator;

public class IpFilter
{

  private List _list=new ArrayList();
  
  public void putContents(String name,Object value)
  { _list.add(new Subnet((String) value));
  }

  public boolean contains(byte[] address)
  { 
    Iterator it=_list.iterator();
    while (it.hasNext())
    { 
      Subnet subnet=(Subnet) it.next();
      if (subnet.contains(address))
      { return true;
      }
    }
    return false;
    
  }

}
