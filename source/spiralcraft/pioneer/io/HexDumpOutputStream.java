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

import spiralcraft.exec.ExecutionContext;
import spiralcraft.vfs.StreamUtil;

public class HexDumpOutputStream
  extends OutputStream
{
  public static void dumpFile(String filename)
    throws IOException
  {
    FileInputStream in=new FileInputStream(filename);
    HexDumpOutputStream out
      =new HexDumpOutputStream
        (new PrintWriter
          (new OutputStreamWriter
            (ExecutionContext.getInstance().out()
            )
          ,true
          )
        );
    StreamUtil.copyRaw(in,out,8192,-1);
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

  @Override
  public synchronized void write(int value)
  {
    short byteValue=Integer.valueOf(value).shortValue();
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

  @Override
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
