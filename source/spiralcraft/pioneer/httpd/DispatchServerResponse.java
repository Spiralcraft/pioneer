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
import javax.servlet.http.HttpServletResponseWrapper;

import javax.servlet.ServletOutputStream;

import spiralcraft.log.ClassLog;

import java.util.Locale;

import java.io.PrintWriter;

import java.io.IOException;

/** 
 * Abstract HttpServletResponse implementation for top level or dispatched
 *   responses
 */
public class DispatchServerResponse
  extends HttpServletResponseWrapper
  implements HttpServletResponse
{
 
  private static final ClassLog log
    =ClassLog.getInstance(DispatchServerResponse.class);
  
  public DispatchServerResponse(HttpServletResponse containingResponse)
  { super(containingResponse);
  }
  
  @Override
  public int getBufferSize()
  { return super.getBufferSize();
  }

  @Override
  public void flushBuffer()
    throws IOException
  { super.flushBuffer();
  }

  @Override
  public void setBufferSize(int bufferSize)
  { super.setBufferSize(bufferSize);
  }

  @Override
  public boolean isCommitted()
  { return super.isCommitted();
  }

  @Override
  public void addCookie(Cookie cookie)
  { 
  }

  @Override
  public boolean containsHeader(String name)
  { return super.containsHeader(name);
  }

  @Deprecated
  @Override
  public String encodeRedirectUrl(String url)
  { return super.encodeRedirectUrl(url);
  }
  
  @Deprecated
  @Override
  public String encodeUrl(String url)
  { return super.encodeUrl(url);
  }

  @Override
  public String encodeRedirectURL(String url)
  { return super.encodeRedirectURL(url);
  }
  
  @Override
  public String encodeURL(String url)
  { return super.encodeURL(url);
  }

  @Override
  public void sendError(int code,String msg) 
  	throws IOException
  {
  }
      
  @Override
  public void sendError(int code) 
    throws IOException
  {
  }

  @Override
  public void sendRedirect(String location)
    throws IOException
  {
  }

  @Override
  public void setLocale(Locale locale)
  { 
  }

  @Override
  public Locale getLocale()
  { return super.getLocale();
  }

  @Override
  public void setStatus(int code)
  { 
  }

  @Deprecated
  @Override
  public void setStatus(int code,String message)
  {
  }


  @Override
  public void setIntHeader(String name, int value)
  {
  }

  @Override
  public void setDateHeader(String name, long date)
  {
  }
  
  @Override
  public void addIntHeader(String name, int value)
  { 
  }

  @Override
  public void addDateHeader(String name,long date)
  { 
  }

  @Override
  public void addHeader(String name, String value)
  { 
  }

  @Override
  public void setHeader(String name, String value)
  {
  }


  @Override
  public void setContentType(String value)
  { log.warning("Ignoring setContentType(\""+value+")\"");
  }

  @Override
  public void setContentLength(int len)
  { 
  }

  @Override
  public void setCharacterEncoding(String encoding)
  {
  }
  
  @Override
  public void reset()
  {
  }
  
  @Override
  public ServletOutputStream getOutputStream()
    throws IOException
  { return super.getOutputStream();
  }

  @Override
  public PrintWriter getWriter()
    throws IOException
  { return super.getWriter();
  }

  @Override
  public String getCharacterEncoding()
  { return super.getCharacterEncoding();
  }

  @Override
  public String getContentType()
  { return super.getContentType();
  }

  @Override
  public void resetBuffer()
  { 
    
  }

}
