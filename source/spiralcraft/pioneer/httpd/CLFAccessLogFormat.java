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

import spiralcraft.time.ClockFormat;

public class CLFAccessLogFormat
  implements AccessLogFormat
{
  private static final DateFormat _dateFormat
    =new SimpleDateFormat("dd/MMM/yyyy:HH:mm:ss Z");

  private final static ClockFormat _formatTimeWatcher
    =new ClockFormat(_dateFormat,1000);
  
  public String header()
  { return null;
  }

  public String format(HttpServerRequest request,HttpServerResponse response)
  { 
    StringBuffer out=new StringBuffer();
    final String user=request.getRemoteUser();
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
      .append(response.getByteCount());
    return out.toString();
  }


 
       
}
