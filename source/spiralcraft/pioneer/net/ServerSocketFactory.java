package spiralcraft.pioneer.net;

import java.net.ServerSocket;
import java.net.InetAddress;
import java.io.IOException;

public interface ServerSocketFactory
{
  public ServerSocket createServerSocket(int port)
    throws IOException;

  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException;

  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException;
}
