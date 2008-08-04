//
// Copyright (c) 1998,2007 Michael Toth
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
package spiralcraft.pioneer.httpd;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Cookie;

import javax.servlet.ServletOutputStream;

import java.util.Locale;

import java.io.PrintWriter;

import java.io.IOException;

/** 
 * Abstract HttpServletResponse implementation for top level or dispatched
 *   responses
 */
public class DispatchServerResponse
  implements HttpServletResponse
{
  private final HttpServletResponse containingResponse;
  
  public DispatchServerResponse(HttpServletResponse containingResponse)
  { this.containingResponse=containingResponse;
  }
  
  public int getBufferSize()
  { return containingResponse.getBufferSize();
  }

  public void flushBuffer()
    throws IOException
  { containingResponse.flushBuffer();
  }

  public void setBufferSize(int bufferSize)
  { containingResponse.setBufferSize(bufferSize);
  }

  public boolean isCommitted()
  { return containingResponse.isCommitted();
  }


  public void addCookie(Cookie cookie)
  { 
  }

  public boolean containsHeader(String name)
  { return containingResponse.containsHeader(name);
  }

  @SuppressWarnings("deprecation") // Implementing Servlet API
  @Deprecated
  public String encodeRedirectUrl(String url)
  { return containingResponse.encodeRedirectUrl(url);
  }
  
  @SuppressWarnings("deprecation")
  @Deprecated
  public String encodeUrl(String url)
  { return containingResponse.encodeUrl(url);
  }

  public String encodeRedirectURL(String url)
  { return containingResponse.encodeRedirectURL(url);
  }
  
  public String encodeURL(String url)
  { return containingResponse.encodeURL(url);
  }

  public void sendError(int code,String msg) 
  	throws IOException
  {
  }
      
  public void sendError(int code) 
    throws IOException
  {
  }

  public void sendRedirect(String location)
    throws IOException
  {
  }

  public void setLocale(Locale locale)
  { 
  }

  public Locale getLocale()
  { return containingResponse.getLocale();
  }

  public void setStatus(int code)
  { 
  }

  @SuppressWarnings("deprecation")
  @Deprecated
  public void setStatus(int code,String message)
  {
  }


  public void setIntHeader(String name, int value)
  {
  }

  public void setDateHeader(String name, long date)
  {
  }
  
  public void addIntHeader(String name, int value)
  { 
  }

  public void addDateHeader(String name,long date)
  { 
  }

  public void addHeader(String name, String value)
  { 
  }

  public void setHeader(String name, String value)
  {
  }


  public void setContentType(String value)
  { 
  }

  public void setContentLength(int len)
  { 
  }

  public void reset()
  {
  }
  
  public ServletOutputStream getOutputStream()
    throws IOException
  { return containingResponse.getOutputStream();
  }

  public PrintWriter getWriter()
    throws IOException
  { return containingResponse.getWriter();
  }

  public String getCharacterEncoding()
  { return containingResponse.getCharacterEncoding();
  }

  public void resetBuffer()
  {
    // XXX ??? TODO Auto-generated method stub
    
  }

}
