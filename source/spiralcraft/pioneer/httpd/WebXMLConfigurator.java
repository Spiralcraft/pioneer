package spiralcraft.pioneer.httpd;

import java.io.IOException;
import java.net.URI;

import org.xml.sax.SAXException;

import spiralcraft.log.ClassLog;
import spiralcraft.sax.AssociationReader;
import spiralcraft.sax.CharactersReader;
import spiralcraft.sax.Element;
import spiralcraft.sax.ElementReader;
import spiralcraft.sax.ParseTree;
import spiralcraft.sax.ParseTreeFactory;
import spiralcraft.vfs.Resource;

/**
 * <p>Configures an HttpServiceContext from a web.xml file
 * </p>
 * 
 * @author mike
 *
 */
public class WebXMLConfigurator
{
  private static final ClassLog log
    =ClassLog.getInstance(WebXMLConfigurator.class);

  private final SimpleHttpServiceContext context;
  private boolean debug=false;
  
  
  
  public WebXMLConfigurator(SimpleHttpServiceContext context)
  { this.context=context;
  }
  
  public void setDebug(boolean debug)
  { 
    this.debug=debug;
    if (this.debug)
    {
    }
  }
  
  public void read(Resource resource)
    throws IOException,SAXException
  {
    ParseTree tree=ParseTreeFactory.fromResource(resource);
    Element root=tree.getDocument().getRootElement();
    log.info("Configuring WAR context from "+resource.getURI());
    
    if (root.getLocalName().equals("web-app"))
    { new WebAppReader().read(root);
    }
    else
    { throwUnexpected(root);
    }
  }
  
  private SAXException throwUnexpected(Element element)
    throws SAXException
  { throw new SAXException("Unexpected element in web.xml: "+element);
  } 
  
  private void ignoring(Element child,Element parent)
  { log.warning("Ignoring web.xml element "+child+" in "+parent);
  }
  
//  private void assertValueRequired(Element element,String childName)
//  { log.fine("web.xml element "+element+" must contain a value for "+childName);
//  }

  class WebAppReader
    extends ElementReader<Void>
  {
    { 
      map(new ContextParamReader());
      map(new FilterReader());
      map(new FilterMappingReader());
      map(new ListenerReader());
      map(new ServletReader());
      map(new ServletMappingReader());
      map(new WelcomeFileListReader());
      map(new JSPConfigReader());
    }
    
    @Override
    protected void skipChild(Element element)
    { ignoring((Element) element.getParent(),element);
    }
    
  }
  
  
  class ContextParamReader
    extends AssociationReader
  {
    
    { 
      elementName="context-param";
      required=true;
    }
 
    public ContextParamReader()
    { super("param-name","param-value");
    }
    
    @Override
    public void close(Element element)
      throws SAXException
    { 
      super.close(element);
      context.setInitParameter
        (get().getKey(),get().getValue());
    }

  }
  
  class ErrorPageReader
    extends ElementReader<ErrorPage>
  {
    {
      elementName="error-page";
      
      map("error-code"
        ,new CharactersReader()
        {
          @Override
          public void close(Element element)
          { 
            super.close(element);
            ErrorPageReader.this.get().setErrorCode(Integer.parseInt(get()));
          }
        }
        );
      
      map("location"
        ,new CharactersReader()
        {
          @Override
          public void close(Element element)
          { 
            super.close(element);
            ErrorPageReader.this.get().setLocation(URI.create(get()));
          }
        }
        );

      map("exception-type"
        ,new CharactersReader()
        {
          @SuppressWarnings("unchecked")
          @Override
          public void close(Element element)
          { 
            super.close(element);
            try
            {
              ErrorPageReader.this.get().setExceptionType
                ((Class<Throwable>) Thread.currentThread()
                  .getContextClassLoader().loadClass(get())
                );
            }
            catch (ClassNotFoundException x)
            { throw new RuntimeException(x);
            }
          }
        }
        );
    
    }
    
    @Override
    public void open(Element element)
    { set(new ErrorPage());
    }

    @Override
    public void close(Element element)
      throws SAXException
    { 
      super.close(element);
      context.addErrorPage(get());
    }  
    
  }
    
  class FilterReader
    extends ElementReader<FilterHolder>
  {
    
    {
      elementName="filter";
      
      map
        ("filter-name"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              FilterReader.this.get().setFilterName(get());
            }
          }
        );
      
      map
        ("filter-class"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              FilterReader.this.get().setFilterClass(get());
            }
          }
        );
      
      map
        ("init-param"
        ,new AssociationReader("param-name","param-value")
          {
            { required=true;
            }
            
            @Override
            public void close(Element element)
              throws SAXException
            { 
              super.close(element);
              FilterReader.this.get()
                .setInitParameter
                  (get().getKey(),get().getValue());
              
            }
          }
        );
     
    }
    
    @Override
    public void open(Element element)
    { set(new FilterHolder());
    }

    @Override
    public void close(Element element)
      throws SAXException
    { 
      super.close(element);
      context.addFilter(get());
    }    
  }
  
  
  class ListenerReader
    extends ElementReader<Void>
  {
    {
      elementName="listener";
      
      map
        ("listener-class"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              context.addListenerClass(get());
            }
          }
        );
    }
  }
  
  class ServletReader
    extends ElementReader<ServletHolder>
  {
    {
      elementName="servlet";
      
      map
        ("servlet-name"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              ServletReader.this.get().setServletName(get());
            }
          }
        );
      
      map
        ("servlet-class"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              ServletReader.this.get().setServletClass(get());
            }
          }
        );
      
      map
        ("init-param"
        ,new AssociationReader("param-name","param-value")
          {
            { required=true;
            }
            
            @Override
            public void close(Element element)
              throws SAXException
            { 
              super.close(element);
              ServletReader.this.get()
                .setInitParameter
                  (get().getKey(),get().getValue());
              
            }
          }
        );
      
      map
        ("load-on-startup"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              ServletReader.this.get().setLoadOnStartup
                (Integer.parseInt(get()));
            }
          }
        );
     
    }
    
    @Override
    public void open(Element element)
    { 
      super.open(element);
      set(new ServletHolder());
    }

    @Override
    public void close(Element element)
      throws SAXException
    { 
      super.close(element);
      context.addServlet(get());
    }
    
  }
  
  class FilterMappingReader
    extends PatternMappingReader<FilterMapping>
  {
    {
      map("dispatcher"
        ,new CharactersReader()
        {
          @Override
          public void close(Element element)
          { 
            super.close(element);
            String condition=get();
            FilterMapping fm=FilterMappingReader.this.get();
            fm.dispatcherSpecified();
            if (condition.equals("REQUEST"))
            { fm.setOnRequest(true);
            }
            else if (condition.equals("INCLUDE"))
            { fm.setOnInclude(true);
            }
            else if (condition.equals("FORWARD"))
            { fm.setOnForward(true);
            }
            else if (condition.equals("ERROR"))
            { fm.setOnError(true);
            }
            else
            { throw new IllegalArgumentException
                ("Unrecognized token '"+condition+"' for dispatcher element");
            }
          }          
        }
        );
    }
    
    public FilterMappingReader()
    { 
      super("filter-name");
      elementName="filter-mapping";
    }
    
    @Override
    public void close(Element element)
      throws SAXException
    {
      super.close(element);
      context.addFilterMapping(get());
    }

    @Override
    public void open(Element element)
    { 
      super.open(element);
      set(new FilterMapping());
    }      
  }

  class ServletMappingReader
    extends PatternMappingReader<PatternMapping>
  {
    public ServletMappingReader()
    { 
      super("servlet-name");
      elementName="servlet-mapping";
    }
    
    @Override
    public void close(Element element)
      throws SAXException
    {
      super.close(element);
      context.setServletMapping(get());
    }
    
    @Override
    public void open(Element element)
    { 
      super.open(element);
      set(new PatternMapping());
    }      
  }
  
  abstract class PatternMappingReader<T extends PatternMapping>
    extends ElementReader<T>
  {
    
    public PatternMappingReader(String nameElement)
    { 

      map
        (nameElement
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              PatternMappingReader.this.get().setName(get());
            }
          }
        );
      
      map
        ("url-pattern"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              PatternMappingReader.this.get().addURLPattern(get());
            }
          }
        );
    }
    
  
  }
  
  class WelcomeFileListReader
    extends ElementReader<Void>
  {
    { 
      elementName="welcome-file-list";
      
      map
        ("welcome-file"
        ,new CharactersReader()
          {
            @Override
            public void close(Element element)
            { 
              super.close(element);
              context.addWelcomeFile(get());
            }
          }
        );
       
    }
  }
  
  class JSPConfigReader
    extends ElementReader<Void>
  {
    { elementName="jsp-config";
    }
  }
  
}
