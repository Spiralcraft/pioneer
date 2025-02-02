package spiralcraft.pioneer.net;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Function;

import javax.net.ssl.X509ExtendedKeyManager;

import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.util.ArrayUtil;

import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;

/**
 * A KeyManager that supports multiple independently managed SSL destinations keyed 
 *   off the SNI.
 * 
 * Each destination can have its own certificate manager and/or keystore/alias
 *   combo that provides a cert with a CN and optionally any number of SANs.
 * 
 */
public class CompoundKeyManager
  extends X509ExtendedKeyManager
{
  private final ClassLog log
    =ClassLog.getInstance(CompoundKeyManager.class);
  private Level logLevel=ClassLog.getInitialDebugLevel
    (getClass(),Level.INFO);
    
  private HashMap<String,KeyManager[]> domainMap=new HashMap<>();
  private ArrayList<KeyManager> keyManagers=new ArrayList<>();
  private ArrayList<KeyManager[]> keySets=new ArrayList<>();
  private HashMap<String,String> domainPrefixMap = new HashMap<>();
  private HashMap<String,KeyManager[]> prefixKeyMap = new HashMap<>();
  private HexFormat hex=HexFormat.of();
  private int prefixDigits=4;
  
  void setLogLevel(Level l)
  { this.logLevel=l;
  }
  
  void addKeyManagers(String[] domains,KeyManager[] kms)
  {
    keyManagers.addAll(Arrays.asList(kms));
    String prefix=hex.toHexDigits(keySets.size(),prefixDigits);

    if (domains!=null)
    {
      for (String domain: domains)
      { 
        if (logLevel.isFine())
        { log.fine("Added keyset for domain ["+domain+"] "+kms);
        }
        domainMap.put(domain, kms);
        domainPrefixMap.put(domain, prefix);
      }
    }
    else
    { 
      if (logLevel.isFine())
      { log.fine("Added default keyset "+kms);
      }
      domainMap.put("*", kms);
      domainPrefixMap.put("*", prefix);
    }
    
    keySets.add(kms);
    prefixKeyMap.put(prefix, kms);
  }

  private String getRequestedServerName(Socket socket)
  { 
    List<SNIServerName> rnl = 
        ((ExtendedSSLSession) ((SSLSocket) socket).getHandshakeSession())
          .getRequestedServerNames();
    if (rnl.size()==0)
    { return null;
    }  
    SNIServerName sn = rnl.get(0);
    String ret=((SNIHostName) sn).getAsciiName();
    if (logLevel.isFine())
    { log.fine("RequestedServerName is ["+ret+"]");
    }
    return ret;
  }

  private String getRequestedServerName(SSLEngine engine)
  { 
    List<SNIServerName> rnl = 
      ((ExtendedSSLSession) engine.getHandshakeSession())
        .getRequestedServerNames();
    if (rnl.size()==0)
    { return null;
    }
    
    SNIServerName sn=rnl.get(0);
    String ret=((SNIHostName) sn).getAsciiName();
    if (logLevel.isFine())
    { log.fine("RequestedServerName is ["+ret+"]");
    }
    return ret;
  }
  
  private String[] mapAliases( Function<KeyManager,String[]> fn)
  {
    ArrayList<String> ret=new ArrayList<>();
    for (int i=0;i<keySets.size();i++)
    {
      String prefix=hex.toHexDigits(i,prefixDigits);
      for (KeyManager km: keySets.get(i))
      {
        String[] aliases= fn.apply(km);
        for (String alias:aliases)
        { 
          if (logLevel.isFine())
          { log.fine("Mapped alias "+prefix+":"+alias);
          }
          ret.add(prefix+":"+alias);
        }
      }
    }
    return ret.toArray(new String[ret.size()]);
  }
  
  @Override
  public String[] getClientAliases(
    String keyType,
    Principal[] issuers)
  {
    return mapAliases
        ( (km) -> 
          ((X509ExtendedKeyManager) km).getClientAliases(keyType,issuers) 
        );
  }

  @Override
  public String chooseEngineClientAlias(
    String[] keyType, 
    Principal[] issuers,
    SSLEngine engine)
  {
    String domain=getRequestedServerName(engine);
    if (domain==null)
    { domain = "*";
    }
    KeyManager[] keySet=domainMap.get(domain);
    if (keySet!=null)
    {
      for (KeyManager km: keySet)
      { 
        String alias=((X509ExtendedKeyManager) km).chooseEngineClientAlias(keyType,issuers,engine);
        if (alias!=null)
        { return domainPrefixMap.get(domain)+":"+alias;
        }
      }
    }
    return null;  
  };
  
  @Override
  public String chooseClientAlias(
    String[] keyType,
    Principal[] issuers,
    Socket socket)
  { 
    String domain=getRequestedServerName(socket);
    if (domain==null)
    { domain = "*";
    }    
    KeyManager[] keySet=domainMap.get(domain);
    if (keySet!=null)
    {
      for (KeyManager km: keySet)
      { 
        String alias=((X509ExtendedKeyManager) km).chooseClientAlias(keyType,issuers,socket);
        if (alias!=null)
        { return domainPrefixMap.get(domain)+":"+alias;
        }
      }
    }
    return null;
  }

  @Override
  public String[] getServerAliases(
    String keyType,
    Principal[] issuers)
  {
    String[] ret = mapAliases
        ( (km) -> 
          ((X509ExtendedKeyManager) km).getServerAliases(keyType,issuers) 
        );  

    if (logLevel.isFine())
    { log.fine("getServerAliases returning "+ArrayUtil.format(ret, "'", ","));
    }
    return ret;
  }

  @Override
  public String chooseEngineServerAlias(
    String keyType, 
    Principal[] issuers,
    SSLEngine engine)
  {
    String domain=getRequestedServerName(engine);
    KeyManager[] keySet=domainMap.get(domain);
    if (keySet==null)
    { 
      domain="*";
      keySet=domainMap.get(domain);
    }
    if (keySet!=null)
    {
      for (KeyManager km: keySet)
      { 
        String alias=((X509ExtendedKeyManager) km).chooseEngineServerAlias(keyType,issuers,engine);
        if (alias!=null)
        { 
          String ret=domainPrefixMap.get(domain)+":"+alias;
          if (logLevel.isFine())
          { log.fine("chooseEngineServerAlias returning "+ret+" for domain "+domain);
          }
          return ret;        
        }
      }
    }
    log.fine("chooseEngineServerAlias returning null for domain "+domain);
    return null;  
  };
    
  @Override
  public String chooseServerAlias(
    String keyType,
    Principal[] issuers,
    Socket socket)
  { 
    String domain=getRequestedServerName(socket);
    KeyManager[] keySet=domainMap.get(domain);
    if (keySet==null)
    { 
      domain="*";
      keySet=domainMap.get(domain);
    }
    if (keySet!=null)
    {
      for (KeyManager km: keySet)
      { 
        String alias=((X509ExtendedKeyManager) km).chooseServerAlias(keyType,issuers,socket);
        if (alias!=null)
        { 
          String ret=domainPrefixMap.get(domain)+":"+alias;
          if (logLevel.isFine())
          { log.fine("chooseServerAlias returning "+ret+" for domain "+domain);
          }
          return ret;
        }
      }
    }
    if (logLevel.isFine())
    { log.fine("chooseServerAlias returning null for domain "+domain);
    }
    return null;
  }

  @Override
  public X509Certificate[] getCertificateChain(
    String alias)
  {
    String prefix=alias.substring(0,prefixDigits);
    String subAlias=alias.substring(prefixDigits+1);
    KeyManager[] kms=prefixKeyMap.get(prefix);
    for (KeyManager km: kms)
    {
      X509Certificate[] chain = ((X509ExtendedKeyManager) km).getCertificateChain(subAlias);
      if (chain!=null)
      { return chain;
      }
    }
    return null;
  }

  @Override
  public PrivateKey getPrivateKey(
    String alias)
  {
    String prefix=alias.substring(0,prefixDigits);
    String subAlias=alias.substring(prefixDigits+1);
    KeyManager[] kms=prefixKeyMap.get(prefix);
    for (KeyManager km: kms)
    {
      PrivateKey key = ((X509ExtendedKeyManager) km).getPrivateKey(subAlias);
      if (key!=null)
      { return key;
      }
    }
    return null;
  }
}