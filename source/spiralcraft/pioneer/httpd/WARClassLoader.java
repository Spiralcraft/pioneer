package spiralcraft.pioneer.httpd;

import java.io.IOException;

import spiralcraft.classloader.JarArchive;
import spiralcraft.classloader.Loader;
import spiralcraft.classloader.ResourceArchive;
import spiralcraft.vfs.Resource;
import spiralcraft.vfs.file.FileResource;


/**
 * <P>ClassLoader implementation to load classes as per standard J2EE webapp
 *   deployment
 * </P>
 * 
 * @author mike
 */
public class WARClassLoader
  extends Loader
{

  public WARClassLoader(Resource warRoot) 
    throws IOException
  { 
    super(Thread.currentThread().getContextClassLoader());
    Resource classesResource=warRoot.asContainer().getChild("classes");
    if (classesResource.exists())
    { addPrecedentArchive(new ResourceArchive(classesResource));
    }
    
    Resource libResource=warRoot.asContainer().getChild("lib");
    if (libResource.exists())
    {
      for (Resource res: libResource.asContainer().listContents())
      {
        if (res.getLocalName().endsWith(".jar"))
        { addPrecedentArchive(new JarArchive((FileResource) res));
        }
      }
    }
  }
  
}