package spiralcraft.pioneer.net;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;

public class StandardServerSocketFactory
  implements ServerSocketFactory
{
  public ServerSocket createServerSocket(int port)
    throws IOException
  { return new ServerSocket(port);
  }

  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException
  { return new ServerSocket(port,backlog);
  }

  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException
  { return new ServerSocket(port,backlog,address);
  }
}
