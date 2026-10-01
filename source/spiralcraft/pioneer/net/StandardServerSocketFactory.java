//
// Copyright (c) 1998,2008 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//
package spiralcraft.pioneer.net;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import java.net.InetAddress;
import java.io.IOException;
import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

public class StandardServerSocketFactory
  implements ServerSocketFactory
{

  private static ClassLog log=ClassLog.getInstance(StandardServerSocketFactory.class);

  private Level logLevel=ClassLog.getInitialDebugLevel
      (getClass(),Level.INFO);

  private int closeTimeoutMs=2000;

  /**
   * Maximum time, in milliseconds, that closeSocket() spends draining inbound
   * data to consume the peer's close_notify. Bounds the close path against
   * peers that have gone away. Default 2000.
   */
  public void setCloseTimeoutMs(int ms)
  { this.closeTimeoutMs=ms;
  }

  @Override
  public ServerSocket createServerSocket(int port)
    throws IOException
  { return new ServerSocket(port);
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException
  { return new ServerSocket(port,backlog);
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException
  { return new ServerSocket(port,backlog,address);
  }
  
  @Override
  public int getMaxOutputFragmentLength(Socket sock)
  { return Integer.MAX_VALUE;
  }
  
  @Override
  public void start()
  {
  }
  
  @Override
  public void stop()
  {
  }
  
  public void configureConnectedSocket(Socket sock)
  {
  }
  
  
  public void abortSocket(Socket sock)
  {
    try
    { sock.setSoTimeout(closeTimeoutMs);
    }
    catch (SocketException x)
    { /* best effort */
    }
    try
    { sock.setSoLinger(true,0);
    }
    catch (SocketException x)
    { /* best effort */ 
    }
    try
    { sock.close();
    }
    catch (IOException x)
    { log.fine("Error aborting socket "+sock+" : "+x);
    }
    if (logLevel.isFine())
    { log.fine("Aborted socket "+sock);
    }
      
  }
  
  @Override
  public void closeSocket(Socket sock)
  { 
  
    if (sock.isClosed())
    { return;
    }


    try
    {
      sock.getOutputStream().flush();
    }
    catch (IOException x)
    {
    }
    
    try
    { 
      sock.shutdownOutput();
    }
    catch (IOException x)
    {
    }
    
    try
    {
      if (!sock.isClosed())
      { sock.close();
      }
    }
    catch (IOException x)
    { log.fine("Error closing socket "+sock+" : "+x);
    }
  }
}
