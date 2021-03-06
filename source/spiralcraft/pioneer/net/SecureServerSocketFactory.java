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
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

//import java.security.Security;

import spiralcraft.vfs.Resource;
import spiralcraft.common.LifecycleException;
import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.util.ArrayUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

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
  private CertManager certManager;
  private String[] enableAdditionalProtocols;

  
  public void setLogLevel(Level logLevel)
  { this.logLevel=logLevel;
  }
  
  public void setKeystoreResource(Resource val)
  { _keystoreResource=val;
  }

  public void setPassphrase(String val)
  { _passphrase=val;
  }

  public void setEnableAdditionalProtocols(String[] protocols)
  { this.enableAdditionalProtocols=protocols;
  }
  
  public void setKeyAlias(String val)
  { _keyAlias=val;
  }
  
  public void setCertManager(CertManager certManager)
  { this.certManager=certManager;
  }
  
  public void start()
    throws LifecycleException
  { 
    if (certManager!=null)
    { 
      certManager.setKeystoreInfo(_keystoreResource,_passphrase,_keyAlias);
      certManager.start();
      try
      { certManager.refreshKeystore();
      }
      catch (Exception x)
      { log.log(Level.WARNING,"Error refreshing keystore- using existing key",x);
      }
    }
    
    try
    { makeFactory();
    }
    catch (IOException x)
    { throw new LifecycleException("Error starting socket factory",x);
    }
  }

  public void stop()
    throws LifecycleException
  { 
    if (certManager!=null)
    { certManager.stop();
    }
  
    _delegate=null;
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

//      KeyManagerFactory kmf=KeyManagerFactory.getInstance("SunX509");
      KeyManagerFactory kmf
        =KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      kmf.init(ks,passphrase);
      KeyManager[] origKeyManagers=kmf.getKeyManagers();
      KeyManager[] keyManagers;
      if (certManager!=null)
      { 
        keyManagers=new KeyManager[origKeyManagers.length];
        Arrays.setAll
          (keyManagers
          , i->certManager.decorateKeyManager(origKeyManagers[i])
          );
      }
      else
      { keyManagers=origKeyManagers;
      }
      

      if (logLevel.canLog(Level.DEBUG))
      {
        for (KeyManager keyManager : keyManagers)
        { 
          if (logLevel.isFine())
          { log.fine("KeyManager: "+keyManager);
          }
        }
      }
          
      _sslContext.init(keyManagers,null,null);
      if (logLevel.isInfo())
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
  { return configureServerSocket(_delegate.createServerSocket(port));
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog)
    throws IOException
  { return configureServerSocket(_delegate.createServerSocket(port,backlog));
  }

  @Override
  public ServerSocket createServerSocket(int port,int backlog,InetAddress address)
    throws IOException
  { return configureServerSocket(_delegate.createServerSocket(port,backlog,address));
  }
  

  protected ServerSocket configureServerSocket(ServerSocket socket)
  { 
    // TODO: Apply custom protocol and cipher suite configuration options
      SSLServerSocket sslSocket=(SSLServerSocket) socket;
      if (enableAdditionalProtocols!=null)
      {
        sslSocket.setEnabledProtocols
          (ArrayUtil.concat
            (sslSocket.getEnabledProtocols()
            ,enableAdditionalProtocols
            )
          );
      }

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
        log.config("Ciphers: Enabled:"
            +ArrayUtil.format(enabledCiphers,"|",null)
            +" Supported:"
            +ArrayUtil.format(ciphers,"|",null)
            );
        
        
      }
      
//      sslSocket.setEnabledProtocols(protocols);
//      sslSocket.setEnabledCipherSuites(ciphers);
    if (certManager!=null)
    { certManager.socketReady();
    }
    return socket;
  
  }
  
  public void configureConnectedSocket(Socket sock)
    throws IOException
  {
    SSLSocket sslSocket=(SSLSocket) sock;
    sslSocket.setHandshakeApplicationProtocolSelector
      (
        (serverSocket, clientProtocols) -> 
        {
          SSLSession handshakeSession = serverSocket.getHandshakeSession();
          // callback function called with current SSLSocket and client AP values
          // plus any other useful information to help determine appropriate
          // application protocol. Here the protocol and ciphersuite are also
          // passed to the callback function.
          return chooseApplicationProtocol(
              serverSocket,
              clientProtocols,
              handshakeSession.getProtocol(),
              handshakeSession.getCipherSuite());
        }
      );
    sslSocket.startHandshake();

    // After the handshake, get the application protocol that has been
    // returned from the callback method.

    String ap = sslSocket.getApplicationProtocol();
    if (logLevel.isFine())
    { log.fine("Application Protocol server side: \"" + ap + "\"");
    }
  }
  
  public String chooseApplicationProtocol
    (SSLSocket serverSocket
    ,List<String> clientProtocols
    , String protocol
    , String cipherSuite 
    )
  {
    if (logLevel.isFine())
    {
      log.fine("Client protocols: "+ArrayUtil.format(clientProtocols.toArray(),",","\""));
      log.fine("Protocol: "+protocol);
      log.fine("Cipher suite: "+cipherSuite);
    }
    
    if (clientProtocols.contains("acme-tls/1"))
    { return "acme-tls/1";
    }
    return "";
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

