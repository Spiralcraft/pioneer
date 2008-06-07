//
// Copyright (c) 1998,2008 Michael Toth
// Spiralcraft Inc., All Rights Reserved
//
// This package is part of the Spiralcraft project and is licensed under
// a multiple-license framework.
//
// You may not use this file except in compliance with the terms found in the
// SPIRALCRAFT-LICENSE.txt file at the top of this distribution, or available
// at http://www.spiralcraft.org/licensing/SPIRALCRAFT-LICENSE.txt.
//
// Unless otherwise agreed to in writing, this software is distributed on an
// "AS IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.
//
/**
 * Provides information from the context in which this
 *   request is being handled.
 */
package spiralcraft.pioneer.httpd;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.FilterChain;

import javax.servlet.http.HttpServletResponse;


import java.io.IOException;

import spiralcraft.pioneer.io.Governer;

public interface HttpServiceContext
  extends ServletContext
{

  /**
   * Process a request
   */
  public void service(AbstractHttpServletRequest request,HttpServletResponse response)
    throws ServletException,IOException;
  
  public void startService();
  
  public void stopService();

  /**
   * Return the Session Manager associated with
   *   this Service Context
   */
  public HttpSessionManager getSessionManager();

  /**
   * Return the AccessLog associated with
   *   this Service Context, which will be inherited from a parent context if
   *   none is supplied via the setAccessLog() method.
   */
  public AccessLog getAccessLog();
  
  /**
   * The hostname of the server
   */
  public String getName();

  /**
   * Return the part of the request path handled by the
   *   the HttpServiceContext.
   */
  public String getAlias();

  /**
   * The server port
   */ 
  public int getPort();
  
  /**
   * The alias that applies to all URIs handled
   *   by this ServiceContext. Taken into consideration
   *   by getRealPath()
   */
  public void setAlias(String alias);

  /**
   * Get the FilterChain with the specified name. Used
   *   internally by contexts with parents.
   */
  FilterChain getFilterChain(String name)
    throws ServletException;

  /**
   * Get the name of the servlet that handles requests
   *  for files ending in the specified extension.
   */
  public String getServletNameForRequestType(String type);

  /**
   * Get the name of the servlet that handles requests
   *  for a named segment of the request path.
   */
  public String getServletNameForAlias(String alias);

  /**
   * Specify the context used to resolve servlet names
   *   and attributes not found in this context.
   */
  public void setParentContext(HttpServiceContext context);

  /**
   * Specify the Governer which will limit the outbound 
   *   throughput this ServletContext and its subcontexts.
   */
  public void setOutputGoverner(Governer governer);

  /**
   * Specify the maximum throughput per individual 
   *   data stream.
   */
  public void setMaxStreamBitsPerSecond(int bps);

  /**
   * Map a mime type to a file extension
   */
  public String mapMimeType(String extension);
  
  /**
   * Perform any required actions before handling the request in a normal fashion.
   *
   *@return true if the request should proceed normally, false otherwise.
   */
  public boolean preFilter
    (AbstractHttpServletRequest request
    ,HttpServletResponse response)
    throws IOException,ServletException;
}
