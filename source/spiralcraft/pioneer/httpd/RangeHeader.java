package spiralcraft.pioneer.httpd;

/**
 * Interprets the syntax of a Range header
 */
public class RangeHeader
{
  
  private int maxBytes=-1;
  private int skipBytes=0;
  private int lastByte=Integer.MAX_VALUE;
  
  public RangeHeader(String httpHeaderValue)
  { 
    // XXX A range header can contain multiple range sets
    // XXX Fix to only use the first, so exception not thrown
    int commaPos=httpHeaderValue.indexOf(',');
    if (commaPos>0)
    { httpHeaderValue=httpHeaderValue.substring(0,commaPos);
    }
    
    int eqPos=httpHeaderValue.indexOf('=');
    if (eqPos==-1)
    { return;
    }
    if (!httpHeaderValue.substring(0,eqPos).equals("bytes"))
    { return;
    }
    int dashPos=httpHeaderValue.indexOf('-');
    if (dashPos==-1)
    { return;
    }
    String skipBytes=httpHeaderValue.substring(eqPos+1,dashPos);
    if (skipBytes.length()>0)
    { this.skipBytes=Integer.parseInt(skipBytes);
    }
    
    String stopBytes=httpHeaderValue.substring(dashPos+1);
    if (stopBytes.length()>0)
    { 
      this.lastByte=Integer.parseInt(stopBytes);
      this.maxBytes=this.lastByte-this.skipBytes;
    }
    
    return;
  }
  
  /**
   * The maximum number of bytes to return
   */
  public int getMaxBytes()
  { return maxBytes;
  }

  /**
   * The position of the last byte to return
   */
  public int getLastByte()
  { return lastByte;
  }
  
  /**
   * The number of bytes at the beginning of the file to ignore
   */
  public int getSkipBytes()
  { return skipBytes;
  }
  
}