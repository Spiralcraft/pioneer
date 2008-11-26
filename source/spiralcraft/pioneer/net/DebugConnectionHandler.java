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

import java.net.Socket;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import spiralcraft.pioneer.io.StreamPump;
import spiralcraft.pioneer.io.HexDumpOutputStream;

/**
 * Debugs incoming server connections.
 */
public class DebugConnectionHandler
  implements ConnectionHandler
{

  public void handleConnection(Socket sock)
  {
    try
    {
      InputStream in=sock.getInputStream();
      OutputStream sockOut=sock.getOutputStream();
      
      OutputStream out=new HexDumpOutputStream(new PrintWriter(new OutputStreamWriter(System.out)));
      StreamPump streamPump=new StreamPump(in,out);
      streamPump.setBufferSize(1024);
      streamPump.setCheckAvailable(true);
      streamPump.setAlwaysBlock(true);
      streamPump.setDebug(true);
      streamPump.run();
      out.flush();
      
      sockOut.write("DEBUG SERVER".getBytes());
      sockOut.flush();
    }
    catch (IOException x)
    { 
      ClassLog.getInstance
        (DebugConnectionHandler.class).log(Level.INFO,x.toString());
    }

  }

}
