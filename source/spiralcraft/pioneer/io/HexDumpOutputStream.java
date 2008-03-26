/**
 * Formats all output into a hex-dump into
 *   a PrintWriter.
 */
package spiralcraft.pioneer.io;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.FileInputStream;
import java.io.OutputStreamWriter;

import spiralcraft.vfs.StreamUtil;

public class HexDumpOutputStream
  extends OutputStream
{
  public static void main(String[] args)
    throws IOException
  {
    FileInputStream in=new FileInputStream(args[0]);
    HexDumpOutputStream out
      =new HexDumpOutputStream
        (new PrintWriter(new OutputStreamWriter(System.out),true));
    StreamUtil.copyRaw(in,out,-1,8192);
    out.flush();
    out.close();
    in.close();
  }

  public HexDumpOutputStream(PrintWriter writer)
  {
    _writer=writer;    
  }

  public HexDumpOutputStream(OutputStream out)
  {
    _writer=new PrintWriter(new OutputStreamWriter(out));
  }

  public synchronized void write(int value)
  {
    short byteValue=new Integer(value).shortValue();
    if (byteValue<0)
    { byteValue+=128;
    }
    String hex=Integer.toHexString(byteValue);
    if (hex.length()==1)
    { _writer.print("0");
    }
    _writer.print(hex);
    _writer.print(' ');
    if (byteValue<28)
    { _buffer.append(".");
    }
    else
    { _buffer.append((char) byteValue);
    }
    _col++;
    if (_col==16)
    {
      _writer.println(_buffer);
      _buffer.setLength(0);
      _col=0;
    }
  }

  public synchronized void flush()
  { 
    for (int i=_col;i<16;i++)
    { _writer.print("   ");
    }
    _writer.println(_buffer);
    _writer.flush();
    _buffer.setLength(0);
    _col=0;
  }

  private int _col;
  private PrintWriter _writer;
  private StringBuffer _buffer=new StringBuffer(8);
}
