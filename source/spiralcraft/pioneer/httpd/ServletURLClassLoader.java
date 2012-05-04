package spiralcraft.pioneer.httpd;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import spiralcraft.vfs.Resource;

/**
 * <p>ClassLoader implementation to load classes as per standard J2EE webapp
 *   deployment
 * </p>
 * 
 * @author mike
 */
public class ServletURLClassLoader
  extends URLClassLoader
{
  
  private static URL[] urlsFromResource(Resource warRoot,Resource[] libResources)
    throws IOException
  {
    ArrayList<URL> list=new ArrayList<URL>();
    Resource classesResource=warRoot.asContainer().getChild("classes");
    if (classesResource.exists())
    { list.add(classesResource.getURI().toURL());
    }
    
    Resource libResource=warRoot.asContainer().getChild("lib");
    addJars(libResource,list);
    
    if (libResources!=null)
    { 
      for (Resource resource : libResources)
      { addJars(resource,list);
      }
    }
    return list.toArray(new URL[list.size()]);
  }

  private static void addJars(Resource libResource,ArrayList<URL> list)
    throws IOException
  {
    if (libResource.exists())
    {
      for (Resource res: libResource.asContainer().listContents())
      {
        if (res.getLocalName().endsWith(".jar"))
        { list.add(res.getURI().toURL());
        }
      }
    }
    
  }
  
  public ServletURLClassLoader(Resource warRoot,Resource[] libResources) 
    throws IOException
  { 
    super(urlsFromResource(warRoot,libResources)
      ,Thread.currentThread().getContextClassLoader()
      );
  }

}