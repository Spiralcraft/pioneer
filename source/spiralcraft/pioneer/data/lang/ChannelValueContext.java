package spiralcraft.pioneer.data.lang;

import com.spiralcraft.data.lang.ValueContext;

import spiralcraft.lang.Channel;

public class ChannelValueContext
  implements ValueContext
{
  private final Channel channel;
  
  public ChannelValueContext(Channel channel)
  { this.channel=channel;
  }
  
  public Object getValue()
  { return channel.get();
  }
  
  public Class getValueClass(ClassLoader classLoader)
  { return channel.getContentType();
  }
}
