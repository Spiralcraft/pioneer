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
 * Implements ServletOutputStream
 */
package spiralcraft.pioneer.httpd;

import javax.servlet.ServletOutputStream;

import java.io.OutputStream;
import java.io.IOException;

import spiralcraft.util.ByteBuffer;

import spiralcraft.pioneer.log.LogManager;
import spiralcraft.pioneer.log.Log;

import spiralcraft.util.StringUtil;

import spiralcraft.pioneer.io.Governer;
import spiralcraft.pioneer.io.GovernedOutputStream;

public class ServerOutputStream
  extends ServletOutputStream
{
  private OutputStream _out;
  private OutputStream _trace;
  private boolean _chunking;
  private final ByteBuffer _buffer;
  private final ByteBuffer _chunkBuffer;
  private boolean _prepared=false;
  private HttpServerResponse _response;
  private Log _log=LogManager.getGlobalLog();
  private static final byte[] CRLF="\r\n".getBytes();
  private int _count=0;
  private boolean _buffering=true;
  private int _bufferSize=128*1024;
  private HttpServer _server;
  
  public ServerOutputStream(HttpServerResponse resp,int initialBufferCapacity)
  {
    _response=resp;
    _buffer=new ByteBuffer(initialBufferCapacity);
    _chunkBuffer=new ByteBuffer(initialBufferCapacity);
  }

  public void setHttpServer(HttpServer server)
  { _server=server;
  }

  public void setTraceStream(OutputStream traceStream)
  { _trace=traceStream;
  }

  public void start(OutputStream out)
  {
    _out=out;
    _chunking=false;
    _buffer.clear();
    _chunkBuffer.clear();
    _prepared=false;
    _count=0;
    _buffering=true;
  }

  public void setChunking(boolean chunking)
  { _chunking=chunking;
  } 

  public void setGoverner(Governer governer)
  { _out=new GovernedOutputStream(_out,governer);
  }

  /**
   * Writes ASCII string data.
   */
  public final void write(final String data)
    throws IOException
  {
    if (data==null)
    { return;
    }
    final char[] chars=data.toCharArray();
    final byte[] bytes=new byte[chars.length];
    for (int i=chars.length-1;i>=0;i--)
    { bytes[i]=(byte) chars[i];
    }
    write(bytes,0,bytes.length);
  }

  public final void write(final int data)
    throws IOException
  { 
    final byte[] bytes={(byte) data};
    write(bytes,0,1);
  }

  public final void write(final byte[] data,final int start,final int len)
    throws IOException
  { 
    if (!_prepared)
    { prepare();
    }
    if (!_buffering)
    {
      if (!_chunking)
      { 
        _out.write(data,start,len);
        _out.flush();
        _server.wroteBytes(_chunkBuffer.length());
        if (_trace!=null)
        {
          _trace.write(data,start,len);
          _trace.flush();
        }
      }
      else
      {
        _chunkBuffer.clear();
        final byte[] sizeBytes
          =StringUtil.asciiBytes
            (Integer.toString(len,16)
            );
        _chunkBuffer.append(sizeBytes);
        _chunkBuffer.append(CRLF);
        _chunkBuffer.append(data);
        _chunkBuffer.append(CRLF);
        _out.write(_chunkBuffer.toByteArray());
        _out.flush();
        _server.wroteBytes(_chunkBuffer.length());
        if (_trace!=null)
        {
          _trace.write(_chunkBuffer.toByteArray());
          _trace.flush();
        }
      }
    }
    else
    { 
      if (_bufferSize>0)
      {
        if (len>_bufferSize)
        { 
          if (_buffer.length()>0)
          { flush();
          }
          _out.write(data,start,len);
          _out.flush();
          _server.wroteBytes(len);
          if (_trace!=null)
          {
            _trace.write(data,start,len);
            _trace.flush();
          }
        }
        else if (len+_buffer.length()>_bufferSize)
        { 
          flush();
          _buffer.append(data,start,len);
        }
        else
        { _buffer.append(data,start,len);
        }
      }
      else
      { _buffer.append(data,start,len);
      }
    }
    _count+=len;
  }

  public void write(byte[] data)
    throws IOException
  { write(data,0,data.length);
  }

  public void setBuffering(boolean buffering)
  { _buffering=buffering;
  }

  private void prepare()
    throws IOException
  {
    if (!_prepared)
    {
      _prepared=true;
      _response.sendHeaders();
    }
  }

  public void flush()
    throws IOException
  {
    if (!_prepared)
    { prepare();
    }
    super.flush();



    if (!_chunking)
    { 
      if (_buffer.length()>0)
      { 
        _out.write(_buffer.toByteArray());
        _out.flush();
        _server.wroteBytes(_buffer.length());
        if (_trace!=null)
        {
          _trace.write(_buffer.toByteArray());
          _trace.flush();
        }
        _buffer.clear();
      }
    }
    else
    {
      if (_buffer.length()>0)
      {
        _chunkBuffer.clear();
        byte[] sizeBytes
          =StringUtil.asciiBytes
            (Integer.toString(_buffer.length(),16)
            );
        _chunkBuffer.append(sizeBytes);
        _chunkBuffer.append(CRLF);
        _chunkBuffer.append(_buffer.toByteArray());
        _chunkBuffer.append(CRLF);
        _out.write(_chunkBuffer.toByteArray());
        _out.flush();
        _server.wroteBytes(_chunkBuffer.length());
        if (_trace!=null)
        {
          _trace.write(_chunkBuffer.toByteArray());
          _trace.flush();
        }
        _buffer.clear();

      }
    }
  }

  public void close()
    throws IOException
  { _out.close();
  }

  public int getCount()
  { return _count;
  }

  public void resetCount()
  { _count=0;
  }

  public int getBufferSize()
  { return _bufferSize;
  }

  public void setBufferSize(int bytes)
  { _bufferSize=bytes;
  }

  
}
