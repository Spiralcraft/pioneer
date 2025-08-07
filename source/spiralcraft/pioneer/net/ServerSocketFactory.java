package spiralcraft.pioneer.net;

import java.net.ServerSocket;
import java.net.Socket;

import spiralcraft.common.Lifecycle;

import java.net.InetAddress;
import java.io.IOException;


public interface ServerSocketFactory
  extends Lifecycle
{
  public ServerSocket createServerSocket(int port)
    throws IOException;

  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException;

  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException;
    
  public int getMaxOutputFragmentLength(Socket socket);
  
  /**
   * Called for each connection
   * @param socket
   */
  public void configureConnectedSocket(Socket socket)
    throws IOException;
  
  public void closeSocket(Socket socket);
}
