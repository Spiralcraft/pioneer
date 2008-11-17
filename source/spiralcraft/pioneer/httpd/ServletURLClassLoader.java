package spiralcraft.pioneer.httpd;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;

import spiralcraft.vfs.Resource;

/**
 * <P>ClassLoader implementation to load classes as per standard J2EE webapp
 *   deployment
 * </P>
 * 
 * @author mike
 */
public class ServletURLClassLoader
  extends URLClassLoader
{
  
  private static URL[] urlsFromResource(Resource warRoot)
    throws IOException
  {
    ArrayList<URL> list=new ArrayList<URL>();
    Resource classesResource=warRoot.asContainer().getChild("classes");
    if (classesResource.exists())
    { list.add(classesResource.getURI().toURL());
    }
    
    Resource libResource=warRoot.asContainer().getChild("lib");
    if (libResource.exists())
    {
      for (Resource res: libResource.asContainer().listContents())
      {
        if (res.getLocalName().endsWith(".jar"))
        { list.add(res.getURI().toURL());
        }
      }
    }
    return list.toArray(new URL[list.size()]);
  }

  public ServletURLClassLoader(Resource warRoot) 
    throws IOException
  { 
    super(urlsFromResource(warRoot)
      ,Thread.currentThread().getContextClassLoader()
      );
  }
  
}