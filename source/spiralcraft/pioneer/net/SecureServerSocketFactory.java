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
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIServerName;

import java.security.GeneralSecurityException;

//import java.security.Security;

import spiralcraft.vfs.Resource;
import spiralcraft.common.LifecycleException;
import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.util.ArrayUtil;

import java.util.List;
import java.util.function.BiFunction;

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

  private KeyContext defaultKeyContext;

  private KeyContext[] keyContexts;
  
  private SSLServerSocketFactory _delegate;
  private SSLContext _sslContext;

  private String[] enableAdditionalProtocols;
  private String[] enabledApplicationProtocols=new String[] {"http/1.1"};
  
  public void setLogLevel(Level logLevel)
  { this.logLevel=logLevel;
  }

  private void useDefaultKeyContext()
  {
    if (logLevel.isFine())
    { log.fine("Using default KeyContext mode");
    }
    if (keyContexts==null && defaultKeyContext==null)
    { defaultKeyContext=new KeyContext();
    }
    else if (keyContexts!=null)
    { 
      throw new IllegalStateException
        ("Cannot set default crypto properties after adding KeyContexts"
        );
    }
    
  }
  
  public void setKeyContexts(KeyContext[] kc)
  {
    if (defaultKeyContext!=null)
    {
      throw new IllegalStateException
        ("Cannot add KeyContexts when using default crypto properties");
    }
    this.keyContexts=kc;
  }
  
  public void setDomains(String[] domains)
  { 
    useDefaultKeyContext();
    defaultKeyContext.setDomains(domains);
  }
  

  public void setKeystoreResource(Resource val)
  { 
    useDefaultKeyContext();
    defaultKeyContext.setKeystoreResource(val);
  }

  public void setPassphrase(String val)
  { 
    useDefaultKeyContext();
    defaultKeyContext.setPassphrase(val);
  }

  public void setEnableAdditionalProtocols(String[] protocols)
  { this.enableAdditionalProtocols=protocols;
  }
  
  public void setKeyAlias(String val)
  { 
    useDefaultKeyContext();
    defaultKeyContext.setKeyAlias(val);
  }
  
  public void setCertManager(CertManager certManager)
  { 
    useDefaultKeyContext();
    defaultKeyContext.setCertManager(certManager);
  }
  
  public void start()
    throws LifecycleException
  { 
//    if (logLevel.isFine())
//    { System.setProperty("javax.net.debug", "ssl");
//    }

    if (keyContexts==null && defaultKeyContext==null)
    { useDefaultKeyContext();
    }
    
    if (defaultKeyContext!=null)
    { keyContexts=new KeyContext[] {defaultKeyContext};
    }
    
    if (keyContexts!=null)
    {
      for (KeyContext kc: keyContexts)
      { kc.start();
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
    if (keyContexts!=null)
    {
      for (KeyContext kc: keyContexts)
      { kc.stop();
      }
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
      CompoundKeyManager keyManager=new CompoundKeyManager();
      keyManager.setLogLevel(logLevel);
      
      if (keyContexts!=null)
      {
        for (KeyContext kc: keyContexts)
        { 
          KeyManager[] keyManagers=kc.initKeyManagers();
          keyManager.addKeyManagers(kc.getDomains(), keyManagers);
        }
      }


      _sslContext=SSLContext.getInstance("TLS");
      _sslContext.init(new KeyManager[] {keyManager},null,null);
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
      SSLParameters params=sslSocket.getSSLParameters();
      params.setApplicationProtocols(enabledApplicationProtocols);
      sslSocket.setSSLParameters(params);
      
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
      String[] applicationProtocols = sslSocket.getSSLParameters().getApplicationProtocols();
      if (logLevel.canLog(Level.CONFIG))
      {
        log.config("Protocols: Enabled: "
                  +ArrayUtil.format(enabledProtocols,"|",null)
                  +" Supported:"
                  +ArrayUtil.format(protocols,"|",null)
                  );
        log.config("Ciphers: Enabled: "
            +ArrayUtil.format(enabledCiphers,"|",null)
            +" Supported:"
            +ArrayUtil.format(ciphers,"|",null)
            );
        log.config("ApplicationProtocols: "
            +ArrayUtil.format(applicationProtocols,"|",null)
            );
        
        
      }
      
//      sslSocket.setEnabledProtocols(protocols);
//      sslSocket.setEnabledCipherSuites(ciphers);
    if (keyContexts!=null)
    { 
      for (KeyContext kc: keyContexts)
      { kc.socketReady();
      }
    }
    return socket;
  
  }
  
  /**
   * Configure the socket that represents a new connection from a client.
   * 
   * Called from HttpConnectionHandler immediately after connection
   */
  public void configureConnectedSocket(Socket sock)
    throws IOException
  {
    if (logLevel.isFine())
    { log.fine("Configuring connected socket "+sock);
    }
    SSLSocket sslSocket=(SSLSocket) sock;

    BiFunction<SSLSocket,List<String>,String> defaultProtocolSelector
      =sslSocket.getHandshakeApplicationProtocolSelector() !=null
      ? sslSocket.getHandshakeApplicationProtocolSelector() 
      : (SSLSocket serverSocket, List<String> clientProtocols) ->
        {
          for (String protocol : clientProtocols)
          { 
            if (ArrayUtil.contains(enabledApplicationProtocols, protocol))
            { return protocol;
            }
          }
          return null;
        };
    
    sslSocket.setHandshakeApplicationProtocolSelector
      (
        (serverSocket, clientProtocols) -> 
        {
          SSLSession handshakeSession = serverSocket.getHandshakeSession();
          // callback function called with current SSLSocket and client AP values
          // plus any other useful information to help determine appropriate
          // application protocol. Here the protocol and ciphersuite are also
          // passed to the callback function.
          String applicationProtocol=
            chooseApplicationProtocol(
              serverSocket,
              clientProtocols,
              handshakeSession.getProtocol(),
              handshakeSession.getCipherSuite()
            );
          if (applicationProtocol==null && defaultProtocolSelector!=null)
          { 
            String ret = defaultProtocolSelector.apply(serverSocket, clientProtocols);
            if (logLevel.isFine())
            { log.fine("defaultProtocolSelector returned applicatioin protocol "+ret);
            }
            return ret;
          }
          else 
          { 
            if (logLevel.isFine())
            { log.fine("handshakeApplicationProtocolSelector returning "+applicationProtocol);
            }
            return applicationProtocol;
          }
        }
      );
    sslSocket.startHandshake();

    if (logLevel.isFine())
    {
      SSLSession session = sslSocket.getSession();
      if (session instanceof ExtendedSSLSession) 
      {
        ExtendedSSLSession extendedSession = (ExtendedSSLSession) session;
        List<SNIServerName> serverNames = extendedSession.getRequestedServerNames();
        if (serverNames != null && !serverNames.isEmpty()) 
        {
            SNIServerName sniServerName = serverNames.get(0);
            String sniHostname = new String(sniServerName.getEncoded(),"UTF-8");
            if (logLevel.isFine())
            { log.fine("SNI Hostname: " + sniHostname);
            }
        }    
      }
    }

    // After the handshake, get the application protocol that has been
    // returned from the callback method.

    String ap = sslSocket.getApplicationProtocol();
    if (logLevel.isFine())
    { log.fine("Application Protocol server side: \"" + ap + "\"");
    }
  }
  
  /**
   * Add support for "acme-tls/1", otherwise defer to default selection.
   * 
   * @param serverSocket
   * @param clientProtocols
   * @param protocol
   * @param cipherSuite
   * @return A mutually acceptable client protocol, null to abort, or empty string
   *           to revert to default behavior.
   */
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
    return null; // defers to default
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

  @Override
  public void closeSocket(Socket sock)
  { 
    SSLSocket sslSocket=(SSLSocket ) sock;
    if (!sslSocket.isClosed())
    {
      try
      {
        sslSocket.getOutputStream().flush();
        sslSocket.shutdownOutput();
        sslSocket.close();
      }
      catch (IOException x)
      { log.fine("Error closing socket "+sock+" : "+x);
      }
    }
  }
}

