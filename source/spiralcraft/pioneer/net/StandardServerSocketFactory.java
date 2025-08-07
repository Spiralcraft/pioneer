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
import java.net.InetAddress;
import java.io.IOException;
import spiralcraft.log.ClassLog;

public class StandardServerSocketFactory
  implements ServerSocketFactory
{

  private static ClassLog log=ClassLog.getInstance(StandardServerSocketFactory.class);
  
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
  
  public void closeSocket(Socket sock)
  { 
    try
    { sock.close();
    }
    catch (IOException x)
    { log.fine("Error closing socket "+sock+" : "+x);
    }
  }
}
