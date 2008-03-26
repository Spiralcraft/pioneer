/**
 * Reads an access log into a keyed map.
 *
 * A single instance of the implementation will
 *   have its readHeader() method called with the header,
 *   followed by a call to readData() for each line in the
 *   file.
 */
package spiralcraft.pioneer.httpd;

import java.util.Map;

public interface AccessLogReader
{


  
  public void readHeader(String header);
  
  public Map readData(String data);

}
