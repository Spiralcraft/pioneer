package spiralcraft.pioneer.net;

import spiralcraft.util.ByteBuffer;

import java.util.StringTokenizer;

public class Subnet
{
  private byte[] _bytes;
  private byte[] _mask;

  public Subnet(String address)
  {
    int slashPos=address.indexOf('/');
    byte[] bytes;

    if (slashPos>-1)
    { 
      
        _bytes=parse(address.substring(0,slashPos));
        _mask=parse(address.substring(slashPos+1));
    }
    else
    { _bytes=parse(address);
    }
    if (_mask!=null)
    {
      for (int i=0;i<_mask.length;i++)
      { _bytes[i]=(byte) (_bytes[i] & _mask[i]);
      }
    }

  }

  public boolean contains(byte[] address)
  {
    if (_mask!=null)
    {
      for (int i=0;i<_mask.length;i++)
      { 
        if ((address[i] & _mask[i]) != _bytes[i])
        { return false;
        }
      }
      return true;
    }
    else
    {
      for (int i=0;i<_bytes.length;i++)
      { 
        if (address[i] != _bytes[i])
        { return false;
        }
      }
      return true;
    }
  }

  public byte[] parse(String addr)
  {
    ByteBuffer buf=new ByteBuffer();
    StringTokenizer tok=new StringTokenizer(addr,".");
    while (tok.hasMoreTokens())
    { buf.append((byte) Integer.parseInt(tok.nextToken()));
    }
    return buf.toByteArray();
  }

}
