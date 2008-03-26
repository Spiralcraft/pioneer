/**
 * Provides the items that the pool manages
 */
package spiralcraft.pioneer.pool;

public interface ResourceFactory
{
  /**
   * Create a new instance of a resource to be added to the Pool.
   */
  public Object createResource();

  /**
   * Discard a resource when no longer needed by the Pool.
   */
  public void discardResource(Object resource);

}
