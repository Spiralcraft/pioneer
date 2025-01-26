package spiralcraft.pioneer.httpd;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import spiralcraft.common.Lifecycle;
import spiralcraft.lang.Contextual;
import spiralcraft.meter.MeterContext;

/**
 * An isolated servicing subunit of an HttpServer with a well
 *   defined scope, typically a web property identified by one or
 *   more related domain names
 */
public interface HttpServerContext
  extends Lifecycle,Contextual
{

  /**
   * A list of lower case hostnames that map to this context.
   * 
   * Optional if this context is a catch-all.
   * 
   * @return
   */
  String[] getHostNames();
  
  /**
   * Process a request
   */
  void service(AbstractHttpServletRequest request,HttpServletResponse response)
    throws ServletException,IOException;
    
  void setDebugSettings(DebugSettings debugSettings);
  
  DebugSettings getDebugSettings();

  void installMeter(MeterContext meterContext);    
}