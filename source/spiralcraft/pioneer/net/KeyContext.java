package spiralcraft.pioneer.net;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;

import spiralcraft.common.Lifecycle;
import spiralcraft.common.LifecycleException;
import spiralcraft.log.ClassLog;
import spiralcraft.log.Level;
import spiralcraft.vfs.Resource;

/**
 * Manages the crypto artifacts for a domain served by an SSL endpoint.
 */
public class KeyContext
  implements Lifecycle
{
  private final ClassLog log
    =ClassLog.getInstance(SecureServerSocketFactory.class);
  private Level logLevel=ClassLog.getInitialDebugLevel
    (getClass(),Level.INFO);

  private Resource keystoreResource;
  private String passphrase="passphrase";
  private String keyAlias;
  private CertManager certManager;
  private String[] domains;

  public void setKeystoreResource(Resource r)
  { this.keystoreResource=r;
  }
  
  public void setPassphrase(String p)
  { this.passphrase=p;
  }
  
  public void setKeyAlias(String ka)
  { this.keyAlias=ka;
  }
  
  public void setCertManager(CertManager cm)
  { this.certManager=cm;
  }

  public String[] getDomains()
  { return domains;
  }
  
  public void setDomains(String[] domains)
  { this.domains=domains;
  }
  
  @Override
  public void start()
    throws LifecycleException
  {
    if (certManager!=null)
    { 
      certManager.setKeystoreInfo(keystoreResource,passphrase,keyAlias);
      certManager.start();
      try
      { certManager.refreshKeystore();
      }
      catch (Exception x)
      { log.log(Level.WARNING,"Error refreshing keystore- using existing key",x);
      }
      domains=certManager.getDomains();
    }
    
  }

  @Override
  public void stop()
    throws LifecycleException
  {
    if (certManager!=null)
    { certManager.stop();
    }

    
  }
  
  KeyManager[] initKeyManagers()
    throws GeneralSecurityException,IOException
  {
    char[] passphraseChars=passphrase.toCharArray();
    KeyStore ks=KeyStore.getInstance("JKS");
    if (keystoreResource!=null)
    { ks.load(keystoreResource.getInputStream(), passphraseChars);
    }
    else
    { 
      ks.load
        (SecureServerSocketFactory.class
          .getResourceAsStream("testkeys")
        , passphraseChars
        );
    }
    

    if (   (keyAlias!=null) 
        && (ks.size() > 1) 
        && (ks.containsAlias(keyAlias))
       ) 
    {
      // Make sure that if an alias is specified, it is the only one
      //   in our in-memory copy of the key-store.
      ArrayList<String> deletes=new ArrayList<>();
      Enumeration<String> aliases=ks.aliases();
      while (aliases.hasMoreElements())
      {
        String alias=aliases.nextElement();
        if (!alias.equals(keyAlias))
        { deletes.add(alias);
        }
      }
      for (String alias : deletes)
      { 
        if (logLevel.canLog(Level.DEBUG))
        { log.fine("Deleting alias "+alias+" (!="+keyAlias+")");
        }
        ks.deleteEntry(alias);
      }
    }

//    KeyManagerFactory kmf=KeyManagerFactory.getInstance("SunX509");
    KeyManagerFactory kmf
      =KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    kmf.init(ks,passphraseChars);
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
    return keyManagers;
  }
  
  void socketReady()
  {
    if (certManager!=null)
    { certManager.socketReady();
    }
 
  }
  
}
