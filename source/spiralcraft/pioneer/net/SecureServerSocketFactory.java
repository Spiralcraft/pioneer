//
// Copyright (c) 1998,2009 Michael Toth
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
package spiralcraft.pioneer.net;


import java.net.ServerSocket;
import java.net.InetAddress;

import java.io.IOException;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

import java.security.Security;

import spiralcraft.vfs.Resource;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;

import java.util.Enumeration;

public class SecureServerSocketFactory
  implements ServerSocketFactory
{
  static
  {
    Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider()); 
  }
  private static final ClassLog _log
    =ClassLog.getInstance(SecureServerSocketFactory.class);

  private SSLServerSocketFactory _delegate;
  private SSLContext _sslContext;
  private Resource _keystoreResource;
  private String _passphrase="passphrase";
  private String _keyAlias;

  public void setKeystoreResource(Resource val)
  { _keystoreResource=val;
  }

  public void setPassphrase(String val)
  { _passphrase=val;
  }

  public void setKeyAlias(String val)
  { _keyAlias=val;
  }
  
  private void makeFactory()
    throws IOException
  {
    if (_delegate!=null)
    { return;
    }

    try
    {
      char[] passphrase=_passphrase.toCharArray();
      _sslContext=SSLContext.getInstance("TLS");
      KeyManagerFactory kmf=KeyManagerFactory.getInstance("SunX509");
      KeyStore ks=KeyStore.getInstance("JKS");
      if (_keystoreResource!=null)
      { ks.load(_keystoreResource.getInputStream(), passphrase);
      }
      else
      { 
        ks.load
          (SecureServerSocketFactory.class
            .getResourceAsStream("testkeys")
          , passphrase
          );
      }
      
      if (   (_keyAlias!=null) 
          && (ks.size() > 1) 
          && (ks.containsAlias(_keyAlias))
         ) 
      {
        // Make sure that if an alias is specified, it is the only one
        //   in our in-memory copy of the key-store.
        Enumeration<String> aliases=ks.aliases();
        while (aliases.hasMoreElements())
        {
          String alias=aliases.nextElement();
          if (!alias.equals(_keyAlias))
          { ks.deleteEntry(alias);
          }
        }
      }

      kmf.init(ks,passphrase);
      _sslContext.init(kmf.getKeyManagers(),null,null);
      _log.log
        (Level.INFO
          ,"SSL Init: SSL Session timeout "
          +_sslContext.getServerSessionContext().getSessionTimeout()
        );
      _delegate=_sslContext.getServerSocketFactory();
    }
    catch (GeneralSecurityException x)
    { 
      x.printStackTrace();
      throw new RuntimeException(x.toString());
    }
    
    
  }

  @Override
  public ServerSocket createServerSocket(int port)
    throws IOException
  { 
    makeFactory();
    return _delegate.createServerSocket(port);
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException
  { 
    makeFactory();
    return _delegate.createServerSocket(port,backlog);
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException
  { 
    makeFactory();
    return _delegate.createServerSocket(port,backlog,address);
  }
  


}
