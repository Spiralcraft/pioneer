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


import spiralcraft.io.RotatingFileOutputAgent;
import spiralcraft.log.Level;

import java.io.IOException;

import spiralcraft.util.string.StringUtil;

/**
 * A log that uses a rotating file
 */
public class RotatingFileAccessLog
  extends RotatingFileOutputAgent
  implements AccessLog
{

  private AccessLogFormat _format=new ECLFAccessLogFormat();
 
  { 
    setFilePrefix("access");
    setFileSuffix("log");
    this.setMaxDelayMs(1000);
    this.setMinDelayMs(250);
  }
  

  public void setFormat(AccessLogFormat format)
  { _format=format;
  }

  public final void log
    (final HttpServerRequest request
    ,final HttpServerResponse response
    )
  { 
    try
    { write(StringUtil.asciiBytes(_format.format(request,response)+"\r\n"));
    }
    catch (IOException x)
    { log.log(Level.SEVERE,"Error writing httpd access log",x);
    }
  }  
}
