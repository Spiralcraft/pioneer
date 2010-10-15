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
package spiralcraft.pioneer.httpd;

import java.text.DateFormat;
import java.text.SimpleDateFormat;


import spiralcraft.time.Clock;  
import spiralcraft.time.ClockFormat;

import javax.servlet.http.HttpSession;

/**
 * Extended customized log format
 */
public final class ECLFAccessLogFormat
  implements AccessLogFormat
{

//  private static DateFormat _legacyDateFormat
//    =new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

  private static DateFormat _dateFormat
    =new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

  private final static ClockFormat _formatTimeWatcher
    =new ClockFormat(_dateFormat,1000);

  @Override
  public String header()
  { return null;
  }

  @Override
  public final String format(final HttpServerRequest request,final HttpServerResponse response)
  { 
    final StringBuffer out=new StringBuffer(256);
    final String user=request.getRemoteUser();
    String referer=request.getHeader("Referer");
    if (referer==null)
    { referer="";
    }
    String userAgent=request.getHeader("User-Agent");
    if (userAgent==null)
    { userAgent="";
    }
    String sessionId="-";
    final HttpSession session=request.getSession(false);
    if (session!=null)
    { sessionId=session.getId();
    }

    String host=request.getHeader("host");
    if (host==null)
    { host="-";
    }
    
    out.append(request.getRemoteAddr())
      .append(" - ")
      .append(user!=null?user:"-")
      .append(" [")
      .append(_formatTimeWatcher.approxTimeFormatted())
      .append("] ")
      .append("\"")
      .append(request.getRequestLine())
      .append("\"")
      .append(" ")
      .append(response.getStatus())
      .append(" ")
      .append(response.getByteCount())
      .append(" ")
      .append("\"")
      .append(referer)
      .append("\"")
      .append(" ")
      .append("\"")
      .append(userAgent)
      .append("\"")
      .append(" ")
      .append(Clock.instance().approxTimeMillis()-request.getStartTime())
      .append(" ")
      .append(sessionId)
      .append(" ")
      .append(host);
    return out.toString();
  }



}
