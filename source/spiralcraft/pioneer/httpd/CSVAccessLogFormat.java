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

import java.util.TimeZone;


import javax.servlet.http.HttpSession;

import spiralcraft.time.Clock;
import spiralcraft.time.ClockFormat;


/**
 * Comma Separated Value access log format.
 */
public final class CSVAccessLogFormat
  implements AccessLogFormat
{

  private static DateFormat _dateFormat
    =new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss");
    { _dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

  private final static ClockFormat _formatTimeWatcher
    =new ClockFormat(_dateFormat,1000);

  @Override
  public String header()
  { 
    return "HOST"
          +","+CLIENT_ADDRESS
          +","+HTTP_AUTH_ID
          +","+TIME
          +","+REQUEST
          +","+REFERER
          +","+USER_AGENT
          +","+RESPONSE_CODE
          +","+BYTE_COUNT
          +","+DURATION
          +","+SESSION_ID
          ;
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
    String sessionId="";
    final HttpSession session=request.getSession(false);
    if (session!=null)
    { sessionId=session.getId();
    }
    appendString(out,request.getServerName());
    out.append(",");
    appendString(out,request.getRemoteAddr());
    out.append(",");
    appendString(out,user);
    out.append(",");
    appendString(out,_formatTimeWatcher.approxTimeFormatted());
    out.append(",");
    appendString(out,request.getRequestLine());
    out.append(",");
    appendString(out,referer);
    out.append(",");
    appendString(out,userAgent);
    out.append(",");
    out.append(response.getStatus());
    out.append(",");
    out.append(response.getByteCount());
    out.append(",");
    out.append(Clock.instance().approxTimeMillis()-request.getStartTime());
    out.append(",");
    appendString(out,sessionId);
    return out.toString();
  }


  public static void appendString(StringBuffer buff,String string)
  {
    buff.append("\"");
    buff.append(escape(string));
    buff.append("\"");
  }

  public static String escape(String input)
  {
    if (input==null)
    { return "";
    }
    StringBuffer out=new StringBuffer();
    int len=input.length();
    char[] chars=input.toCharArray();
    for (int i=0;i<len;i++)
    {
      switch (chars[i])
      {
      case '\r':
        out.append("\\r");
        break;
      case '\n':
        out.append("\\n");
        break;
      case '\t':
        out.append("\\t");
        break;
      case '"':
        out.append("\\\"");
        break;
      case '\\':
        out.append("\\\\");
        break;
      default:
        out.append(chars[i]);
        break;
      }
    }
    return out.toString();
  }
}
