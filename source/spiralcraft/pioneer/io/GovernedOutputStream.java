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
/**
 * An OutputStream which uses a Governer to
 *   keep the data rate below a specified
 *   threshhold.
 *
 */
package spiralcraft.pioneer.io;

import java.io.OutputStream;
import java.io.IOException;

public class GovernedOutputStream
  extends OutputStream
{

  public GovernedOutputStream(OutputStream out,Governer governer)
  { 
    _governer=governer;
    _out=out;
  }

  @Override
  public final void write(byte[] data)
    throws IOException
  { write(data,0,data.length);
  }


  @Override
  public final void write(final byte[] data,final int start,final int len)
    throws IOException
  {
    int total=0;
    while (total<len)
    {
      final int packetSize=_governer.nextPacket(len-total);
      _out.write(data,start+total,packetSize);
      total+=packetSize;
    }
  }

  @Override
  public final void write(int data)
    throws IOException
  { 
    _governer.nextPacket(1);
    _out.write(data);
  }

  @Override
  public final void close()
    throws IOException
  { _out.close();
  }

  @Override
  public final void flush()
    throws IOException
  { _out.flush();
  }

  private final Governer _governer;
  private final OutputStream _out;

}
