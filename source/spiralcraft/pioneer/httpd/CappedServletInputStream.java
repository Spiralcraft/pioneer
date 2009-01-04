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

import java.io.IOException;
import java.io.InputStream;

import spiralcraft.io.CappedInputStream;
import javax.servlet.ServletInputStream;

public class CappedServletInputStream
    extends ServletInputStream
{

  private final CappedInputStream delegate;
  
  public CappedServletInputStream(
      InputStream source,
      long cap)
  {
    delegate=new CappedInputStream(source,cap);
  }

  @Override
  public int read() throws IOException
  { return delegate.read();
  }

  @Override
  public int read(byte[] bytes) throws IOException
  { return delegate.read(bytes);
  }

  @Override
  public int read(byte[] bytes,int start,int len) throws IOException
  { return delegate.read(bytes,start,len);
  }
}
