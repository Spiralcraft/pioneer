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

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import spiralcraft.util.string.StringUtil;

import spiralcraft.pioneer.io.Governer;
import spiralcraft.pioneer.io.GovernedOutputStream;

public class ServerOutputStream
  extends ServletOutputStream
{
  private static final ClassLog log
    =ClassLog.getInstance(ServerOutputStream.class);

  private OutputStream _out;
  private OutputStream _trace;
  private boolean _chunking;
  private final ByteBuffer _buffer;
  private final ByteBuffer _chunkBuffer;
  private boolean _prepared=false;
  private HttpServerResponse _response;
  private static final byte[] CRLF="\r\n".getBytes();
  private int _count=0;
  private boolean _buffering=true;
  private int _bufferSize=128*1024;
  private HttpServer _server;
  private boolean _committed;
  private boolean _closeRequested;
  
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
    _committed=false;
    _closeRequested=false;
  }

  public void resetBuffer()
  {
    if (_committed)
    { throw new IllegalStateException("Response already committed");
    }
    
    _buffer.clear();
    _prepared=false;
    _count=0;
    
  }
  
  public void setChunking(boolean chunking)
  { 
    if (_server.getDebugProtocol())
    { log.fine("Chunking set to "+chunking);
    }
    _chunking=chunking;
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
    if (_server.getDebugProtocol())
    { log.fine("Accepting output: ascii data "+data);
    }
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

  @Override
  public final void write(final int data)
    throws IOException
  { 
    final byte[] bytes={(byte) data};
    write(bytes,0,1);
  }

  @Override
  public final void write(final byte[] data,final int start,final int len)
    throws IOException
  { 
    if (_server.getDebugProtocol())
    { log.fine("Accepting output: "+len+" bytes");
    }
    
    if (!_prepared)
    {
      if (_server.getDebugProtocol())
      { log.fine("Inserting headers before appending content");
      }
      prepare();
      if (_server.getDebugProtocol())
      { log.fine("Finished inserting headers");
      }
    }
    if (!_buffering)
    {
      if (!_chunking)
      { 
        if (_server.getDebugProtocol())
        { log.fine("Not buffered, not chunked");
        }
        writeToClient(data,start,len);
      }
      else
      {
        if (_server.getDebugProtocol())
        { log.fine("Not buffered, chunked");
        }
        
        _chunkBuffer.clear();
        final byte[] sizeBytes
          =StringUtil.asciiBytes
            (Integer.toString(len,16)
            );
        _chunkBuffer.append(sizeBytes);
        _chunkBuffer.append(CRLF);
        _chunkBuffer.append(data);
        _chunkBuffer.append(CRLF);
        writeToClient(_chunkBuffer.toByteArray());
      }
    }
    else
    { 
      if (_bufferSize>0)
      {
        if (len>_bufferSize)
        { 
          if (_server.getDebugProtocol())
          { log.fine("Output exceeds total buffer size");
          }
          
          if (_buffer.length()>0)
          { flush();
          }
          writeToClient(data,start,len);
        }
        else if (len+_buffer.length()>_bufferSize)
        { 
          if (_server.getDebugProtocol())
          { log.fine("Pre-flushing buffer to make room");
          }
          
          flush();

          if (_server.getDebugProtocol())
          { log.fine("Buffering "+len+" bytes");
          }
          _buffer.append(data,start,len);
        }
        else
        { 
          if (_server.getDebugProtocol())
          { log.fine("Buffering "+len+" bytes");
          }

          _buffer.append(data,start,len);
        }
      }
      else
      { 
        if (_server.getDebugProtocol())
        { log.fine("Infinite buffer");
        }
        _buffer.append(data,start,len);
      }
    }
    _count+=len;
  }

  @Override
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
      if (_server.getDebugProtocol())
      { log.fine("Preparing");
      }
      _prepared=true;
      _response.sendHeaders();
    }
  }

  @Override
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

  @Override
  public void close()
    throws IOException
  { 
    if (_server.getDebugProtocol())
    { log.fine("Requested output stream close");
    }
    _closeRequested=true;
  }

  void cleanup()
  {
    if (_closeRequested)
    { 
      try
      { _out.close();
      }
      catch (IOException x)
      {
        if (_server.getDebugProtocol())
        { log.log(Level.DEBUG,"Exception closing underlying stream",x);
        }
      }
    }
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

  public boolean isCommitted()
  { 
    if (_server.getDebugService())
    { log.fine(""+_committed);
    }
    return _committed;
  }
  
  public void setBufferSize(int bytes)
  { 
    if (_committed)
    { throw new IllegalStateException("Response committed");
    }
    
    if (_server.getDebugService())
    { log.fine(""+bytes+" bytes (from "+_bufferSize+")");
    }
    _bufferSize=bytes;
  }

  
  private void writeToClient(byte[] bytes)
    throws IOException
  { writeToClient(bytes,0,bytes.length);
  }
  
  private void writeToClient(byte[] bytes,int start,int len)
    throws IOException
  {
    _committed=true;
    _out.write(bytes,start,len);
    _out.flush();
    _server.wroteBytes(len);
    if (_trace!=null)
    {
      _trace.write(bytes,start,len);
      _trace.flush();
    }
    
  }
  
}
