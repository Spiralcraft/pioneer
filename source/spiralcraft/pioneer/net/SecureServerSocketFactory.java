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
import java.net.Socket;
import java.net.InetAddress;

import java.io.IOException;

import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.KeyManagerFactory;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

//import java.security.Security;

import spiralcraft.vfs.Resource;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Enumeration;

public class SecureServerSocketFactory
  implements ServerSocketFactory
{
//  static
//  {
//    Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider()); 
//  }
  private final ClassLog log
    =ClassLog.getInstance(SecureServerSocketFactory.class);
  private Level logLevel=ClassLog.getInitialDebugLevel
      (getClass(),Level.INFO);

  private SSLServerSocketFactory _delegate;
  private SSLContext _sslContext;
  private Resource _keystoreResource;
  private String _passphrase="passphrase";
  private String _keyAlias;

  
  public void setLogLevel(Level logLevel)
  { this.logLevel=logLevel;
  }
  
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
        ArrayList<String> deletes=new ArrayList<>();
        Enumeration<String> aliases=ks.aliases();
        while (aliases.hasMoreElements())
        {
          String alias=aliases.nextElement();
          if (!alias.equals(_keyAlias))
          { deletes.add(alias);
          }
        }
        for (String alias : deletes)
        { 
          if (logLevel.canLog(Level.DEBUG))
          { log.fine("Deleting alias "+alias+" (!="+_keyAlias+")");
          }
          ks.deleteEntry(alias);
        }
      }

      kmf.init(ks,passphrase);
      _sslContext.init(kmf.getKeyManagers(),null,null);
      if (logLevel.canLog(Level.INFO))
      {
        log.log
          (Level.INFO
          ,"SSL Init: SSL Session timeout "
            +_sslContext.getServerSessionContext().getSessionTimeout()
          );
      }
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
    return configureServerSocket(_delegate.createServerSocket(port));
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException
  { 
    makeFactory();
    return configureServerSocket(_delegate.createServerSocket(port,backlog));
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException
  { 
    makeFactory();
    return configureServerSocket(_delegate.createServerSocket(port,backlog,address));
  }
  

  protected ServerSocket configureServerSocket(ServerSocket socket)
  { 
    // TODO: Apply custom protocol and cipher suite configuration options
      SSLServerSocket sslSocket=(SSLServerSocket) socket;

      String[] protocols=sslSocket.getSupportedProtocols();
      String[] ciphers=sslSocket.getSupportedCipherSuites();

      String[] enabledProtocols=sslSocket.getEnabledProtocols();
      String[] enabledCiphers=sslSocket.getEnabledCipherSuites();
      if (logLevel.canLog(Level.CONFIG))
      {
        log.config("Protocols: Enabled:"
                  +ArrayUtil.format(enabledProtocols,"|",null)
                  +" Supported:"
                  +ArrayUtil.format(protocols,"|",null)
                  );
        log.config("Cyphers: Enabled:"
            +ArrayUtil.format(enabledCiphers,"|",null)
            +" Supported:"
            +ArrayUtil.format(ciphers,"|",null)
            );
        
        
      }

//      sslSocket.setEnabledProtocols(protocols);
//      sslSocket.setEnabledCipherSuites(ciphers);
    
    return socket;
  
  }
  
  @Override
  public int getMaxOutputFragmentLength(Socket socket)
  { 
    SSLParameters sslParams = ((SSLSocket) socket).getSSLParameters();
    int param= sslParams.getMaximumPacketSize()-1024;
    int ret=
      param==0
      ?(15*1024)
      :param>(16*1024)
      ?param-1024
      :param
      ;   
    return ret;
    //return 15*1024;
  }

}
