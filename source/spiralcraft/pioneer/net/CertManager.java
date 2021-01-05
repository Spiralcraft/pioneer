package spiralcraft.pioneer.net;

import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;

import javax.net.ssl.KeyManager;

import spiralcraft.common.Lifecycle;
import spiralcraft.vfs.Resource;

/**
 * Manages the certificate resource for a SecureServerSocketFactory 
 * 
 * @author mike
 *
 */
public interface CertManager
  extends Lifecycle
{
  public void setKeystoreInfo(Resource resource,String passphrase,String keyAlias);
  
  /**
   * Prepare the KeyStore for use by the SSL ServerCocketFactory
   *  
   * @throws IOException
   * @throws KeyStoreException
   * @throws CertificateException
   * @throws NoSuchAlgorithmException
   * @throws UnrecoverableKeyException
   */
  public void refreshKeystore()
    throws IOException
          ,KeyStoreException
          ,CertificateException
          ,NoSuchAlgorithmException
          ,UnrecoverableKeyException
          ;
  
  /**
   * Provide a KeyManager that can insert functionality into the SSL Handshake
   * 
   * @param km
   * @return
   */
  public KeyManager decorateKeyManager(KeyManager km);

  /**
   * Called once the socket is listening
   */
  public void socketReady();
}
