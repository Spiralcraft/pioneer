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
import java.net.Socket;
import java.io.IOException;

import spiralcraft.util.ByteBuffer;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import spiralcraft.util.string.StringUtil;

import spiralcraft.pioneer.io.Governer;
import spiralcraft.pioneer.net.ServerSocketFactory;
import spiralcraft.pioneer.io.GovernedOutputStream;

public class ServerOutputStream
  extends ServletOutputStream
{
  private static final ClassLog log
    =ClassLog.getInstance(ServerOutputStream.class);

  public final static byte[] END_CHUNK="0\r\n\r\n".getBytes();

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
  private int maxWriteSize=Integer.MAX_VALUE;
                                      
  private int _bufferSize=128*1024;
  private DebugSettings debugSettings;
  private boolean _committed;
  private boolean _closeRequested;
  private HttpServer server;
  
  public ServerOutputStream(HttpServerResponse resp,int initialBufferCapacity)
  {
    _response=resp;
    _buffer=new ByteBuffer(initialBufferCapacity);
    _chunkBuffer=new ByteBuffer(initialBufferCapacity);
  }

  public void setDebugSettings(DebugSettings debugSettings)
  { this.debugSettings=debugSettings;
  }

  public void setHttpServer(HttpServer server)
  { this.server=server;
  }
  
  public void setTraceStream(OutputStream traceStream)
  { _trace=traceStream;
  }

  public void start(Socket socket,ServerSocketFactory factory)
    throws IOException
  {
    this.maxWriteSize=factory.getMaxOutputFragmentLength(socket);
    this._out=socket.getOutputStream();
    this._chunking=false;
    this._buffer.clear();
    this._chunkBuffer.clear();
    this._prepared=false;
    this._count=0;
    this._buffering=true;
    this._committed=false;
    this._closeRequested=false;
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
    if (debugSettings.getDebugProtocol())
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
    if (debugSettings.getDebugProtocol())
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
    if (debugSettings.getDebugProtocol())
    { log.fine("Accepting output: "+data+" ("+((char) data)+")");
    }  
    final byte[] bytes={(byte) data};
    write(bytes,0,1);
  }

  @Override
  public final void write(final byte[] data,final int start,final int len)
    throws IOException
  { 
    if (debugSettings.getDebugProtocol())
    { log.fine("Accepting output: "+len+" bytes");
    }
    
    if (!_prepared)
    {
      if (debugSettings.getDebugProtocol())
      { log.fine("Inserting headers before appending content");
      }
      prepare();
      if (debugSettings.getDebugProtocol())
      { log.fine("Finished inserting headers");
      }
    }
    if (!_buffering)
    {
      if (!_chunking)
      { 
        if (debugSettings.getDebugProtocol())
        { log.fine("Not buffered, not chunked");
        }
        writeToClient(data,start,len);
      }
      else
      {
        if (debugSettings.getDebugProtocol())
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
          if (debugSettings.getDebugProtocol())
          { log.fine("Output exceeds total buffer size");
          }
          
          if (_buffer.length()>0)
          { flush();
          }
          writeToClient(data,start,len);
        }
        else if (len+_buffer.length()>_bufferSize)
        { 
          if (debugSettings.getDebugProtocol())
          { log.fine("Pre-flushing buffer to make room");
          }
          
          flush();

          if (debugSettings.getDebugProtocol())
          { log.fine("Buffering "+len+" bytes");
          }
          _buffer.append(data,start,len);
        }
        else
        { 
          if (debugSettings.getDebugProtocol())
          { log.fine("Buffering "+len+" bytes");
          }

          _buffer.append(data,start,len);
        }
      }
      else
      { 
        if (debugSettings.getDebugProtocol())
        { log.fine("Infinite buffer");
        }
        _buffer.append(data,start,len);
      }
    }
    _count+=len;
  }

  void finish()
    throws IOException
  { 
    flush();
    if (_chunking)
    {
      setChunking(false);
      writeToClient(END_CHUNK);
    }
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
      if (debugSettings.getDebugProtocol())
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
    try
    {
      if (debugSettings.getDebugProtocol())
      { log.fine("Flush requested");
      }
      
      if (!_prepared)
      { prepare();
      }
      
      super.flush();
  
  
  
      if (!_chunking)
      { 
        if (_buffer.length()>0)
        { 
          if (debugSettings.getDebugProtocol())
          { log.fine("Writing "+_buffer.length()+" bytes");
          }  
          writeToClient(_buffer.toByteArray());
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
          
          writeToClient(_chunkBuffer.toByteArray());
          _chunkBuffer.clear();
          _buffer.clear();
  
        }
      }

    }
    catch (IOException x)
    {
      if (debugSettings.getDebugProtocol())
      { 
        log.log(Level.WARNING,"Flush fail: ",x);
        log.log(Level.WARNING,"Out =  "+_out);
        log.log(Level.WARNING,_buffer.toAsciiString());
      }
      throw x;
    }
  }

  @Override
  public void close()
    throws IOException
  { 
    if (debugSettings.getDebugProtocol())
    { log.fine("Requested output stream close");
    }
    _closeRequested=true;
  }

  void cleanup()
  {
    // DO NOT CLOSE OUTPUT STREAMS BECAUSE IT MESSES WITH TLS
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
    if (debugSettings.getDebugService())
    { log.fine(""+_committed);
    }
    return _committed;
  }
  
  public void setBufferSize(int bytes)
  { 
    
    if (_committed)
    { throw new IllegalStateException("Response committed");
    }
    
    if (debugSettings.getDebugService())
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

    // Write output in [maxWriteSize] blocks
    int pos=0;
    int count=1;
    while (start+pos<len)
    {
      int writeLen=Math.min(maxWriteSize, len-pos);
      if (debugSettings.getDebugProtocol())
      { log.fine("Writing block "+count+" of "+writeLen+"/"+len+" bytes to client");
      }
      _out.write(bytes,start+pos,writeLen);
      pos+=writeLen;
      count++;
      _out.flush();
    }

    server.wroteBytes(len);
    if (_trace!=null)
    {
      _trace.write(bytes,start,len);
      _trace.flush();
    }
    
  }
  
}
