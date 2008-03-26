/**
 * Provides application specific information
 *   for a RotatingLog.
 */
package spiralcraft.pioneer.io;

public interface RotatingLogSource
{


  /**
   * Return the name of the active
   *   file.
   */
  public String getActiveFilename();

  /**
   * Return a new filename that the 
   *   current active file will be
   *   renamed to.
   */
  public String getNewArchiveFilename();

  /**
   * Return the header to be written at
   *   the beginning of the file. The returned
   *   string must include any termination
   *   characters.
   */
  public byte[] getHeader();


  /**
   * Return a chunk of data to write to 
   *   the file.
   */
  public byte[] getData();

}
