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

package spiralcraft.pioneer.httpd;

import java.io.InputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.io.OutputStream;

import spiralcraft.vfs.StreamUtil;
import spiralcraft.io.NullOutputStream;
import spiralcraft.log.ClassLog;

import javax.servlet.ServletInputStream;

/**
 * Implementation of the abstract ServerInputStream
 */
public final class ServerInputStream
  extends ServletInputStream
{
  private static final ClassLog log
    =ClassLog.getInstance(ServerInputStream.class);
//  private final HttpServer server;
  
  private InputStream _in;

  private byte[] _byteBuffer=new byte[256];
  private char[] _charBuffer=new char[256];
  private int _count=0; 
  private OutputStream _trace;
  private final boolean debugIO;
   
  public ServerInputStream(DebugSettings debugSettings)
  { 
//  this.server=server;
    this.debugIO=debugSettings.getDebugIO();
  }

  public void setTraceStream(OutputStream traceStream)
  { _trace=traceStream;
  }

  
  public int getCount()
  { return _count;
  }

  public void resetCount()
  { _count=0;
  }

  public void start(InputStream in)
  { 
    _in=new BufferedInputStream(in,8192);
    _count=0;
  }

  /**
   * Read and discard b bytes
   */
  public void discard(int bytes)
    throws IOException
  { StreamUtil.copyRaw(_in,new NullOutputStream(),16384,bytes);
  }

  @Override
  public final int read()
    throws IOException
  {
    final int val=_in.read();
    if (val>-1)
    { 
      _count++;
      if (_trace!=null)
      { 
        _trace.write(val);
        _trace.flush();
      }

    }
    return val;
  }

  @Override
  public final int read(byte[] b,int start,int len)
    throws IOException
  {
    final int count=_in.read(b,start,len);
    if (_count>-1)
    { _count+=count;
    }
    
    if (_trace!=null)
    { 
      if (start>-1 && start<b.length && start+count<=b.length)
      { 
        _trace.write(b,start,count);
      }
      else if (count==-1)
      { _trace.write("[EOS]".getBytes());
      }
      else 
      { 
        _trace.write
          (("Index? b.length="+b.length+", start="+start+", count="+count)
            .getBytes()
          );
      }
      _trace.flush();
    }
    return count;
  }

  @Override
  public final int readLine(final byte[] bytes,final int start,final int len)
    throws IOException
  {
    for (int i=0;i<len;i++)
    { 
      final int b=read();
      if (b==-1)
      { return -1;
      }
      bytes[start+i]=(byte) b;
      
      if (b=='\n')
      { return i+1;
      }
    }
    return len;
  }

  /**
   * Read a line of ascii text ending with an \n, which
   *   must be preceded with a \r
   */
  public final String readAsciiLine()
    throws IOException
  {
    String ret=null;
    while (true)
    {
      int count=readLine(_byteBuffer,0,256);
      if (count==-1)
      { break;
      }

      for (int i=0;i<count;i++)
      { _charBuffer[i]=(char) _byteBuffer[i];
      }

      if (_charBuffer[count-1]=='\n')
      { 
    	  if (debugIO)
    	  { log.fine("Found LF");
      	}
    	  
        // Deal with possibility of just a \n as a line term 
        //  from slack clients.
        final int trim=(count==1||_charBuffer[count-2]!='\r')?1:2;

        if (debugIO)
        {
          if (trim==1)
          { 
            if (count==1)
            { log.fine("Buffer only contains an LF");
            }
            else
            { log.fine("Char before LF = '"+(int) _charBuffer[count-2]+"'");
            }
          }
        }
        
        if (ret==null)
        { ret=new String(_charBuffer,0,count-trim);
        }
        else
        { ret=ret.concat(new String(_charBuffer,0,count-trim));
        }
        break;
      }
      else
      {
        final int trim=_charBuffer[count-1]=='\r'?1:0;
        
        if (debugIO)
        {
          if (trim==1)
          { log.fine("Buffer ends in CR");
          }
        }
        
        if (ret==null)
        { ret=new String(_charBuffer,0,count-trim);
        }
        else
        { ret=ret.concat(new String(_charBuffer,0,count-trim));
        }
        if (debugIO)
        { log.fine("Read partial line: "+ret);
        }
      }
    }
    if (debugIO)
    { log.fine("Read complete line: "+ret);
    }
    return ret;
  }

  @Override
  public final void close()
    throws IOException
  { _in.close();
  }
}
