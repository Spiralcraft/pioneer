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

import java.net.URI;

/**
 * 
 * @author mike
 */
public class ErrorPage
{
  private int errorCode;
  private Class<Throwable> exceptionType;
  private URI location;
  
  public int getErrorCode()
  { return errorCode;
  }
  public void setErrorCode(int errorCode)
  { this.errorCode = errorCode;
  }
  
  public Class<Throwable> getExceptionType()
  { return exceptionType;
  }
  
  public void setExceptionType(Class<Throwable> exceptionType)
  { this.exceptionType=exceptionType;
  }
  
  public URI getLocation()
  { return location;
  }
  
  public void setLocation(URI location)
  { this.location = location;
  }
  
  
}
