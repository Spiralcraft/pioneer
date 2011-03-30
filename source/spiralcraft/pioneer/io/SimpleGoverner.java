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
package spiralcraft.pioneer.io;

import spiralcraft.time.Clock;

import spiralcraft.log.Level;
import spiralcraft.log.ClassLog;

import java.io.IOException;
import java.io.InterruptedIOException;

/**
 * A simple implementation of a Governer.
 *
 * Measures traffic using frames of a specific duration. When
 *   a frame is 'full', blocks until the beginning of the next
 *   frame. 
 *
 * Note that extremely low frame durations in relation to the bandwidth
 *   cause excess fragmentation. 
 *
 * Fairness is maintained by VM scheduling policy.
 */
public class SimpleGoverner
  implements Governer
{
  
  private static final ClassLog log=ClassLog.getInstance(SimpleGoverner.class);

  private long _frameStartTime;
  private long _frameBytesSoFar;

  private long _bitsPerSecond=1000000000; // 1 Gbps
  private int _frameSizeMs=250;
  private long _bytesPerFrame;

//  private long _totalBytes;
  
  public void setRateBitsPerSecond(long bitsPerSecond)
  { 
    _bitsPerSecond=bitsPerSecond;
    _bytesPerFrame=Math.max(1,(long) ((_bitsPerSecond/8)*(_frameSizeMs/(float) 1000)));
  }

  public void setFrameSizeMs(int frameSizeMs)
  {
    _frameSizeMs=frameSizeMs;
    _bytesPerFrame=Math.max(1,(long) ((_bitsPerSecond/8)*(_frameSizeMs/(float) 1000)));
  }

  @Override
  public synchronized int nextPacket(int desiredSize)
    throws IOException
  { 
    long now=Clock.instance().approxTimeMillis();
    if (now-_frameStartTime<_frameSizeMs)
    { 
      if (_frameBytesSoFar>=_bytesPerFrame)
      {
        final long sleepTime=(_frameStartTime+_frameSizeMs)-now;
        if (sleepTime>0 && sleepTime<=_frameSizeMs)
        {
          try
          { Thread.sleep(sleepTime);
          }
          catch (InterruptedException x)
          { throw new InterruptedIOException();
          }
        }
        else if (sleepTime>_frameSizeMs)
        { 
          log.log(Level.WARNING
                  ,"Governer attempted to sleep for "+sleepTime+" > "
                  +_frameSizeMs);
        }
        
        // New frame after we block
        _frameStartTime=Clock.instance().approxTimeMillis();
        _frameBytesSoFar=0;
      }
    }
    else
    {
      // New frame because no activity
      _frameStartTime=now;
      _frameBytesSoFar=0;
    }

    int nextPacketSize=(int) Math.min(desiredSize,_bytesPerFrame-_frameBytesSoFar);
    _frameBytesSoFar+=nextPacketSize;
//    _totalBytes+=nextPacketSize;
    return nextPacketSize;
  }



}
