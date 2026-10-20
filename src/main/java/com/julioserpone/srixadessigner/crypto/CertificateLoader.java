package com.julioserpone.srixadessigner.crypto;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Loads a PKCS#12 file without ever logging its password or private key. */
public final class CertificateLoader {

  private CertificateLoader() {}

  /**
   * Loads a PKCS#12 signing identity.
   *
   * @param pkcs12 file contents
   * @param password password for the PKCS#12
   * @param alias optional private-key alias; when null the only private key entry is used
   * @return a validated signing identity
   */
  public static SigningIdentity load(byte[] pkcs12, char[] password, String alias) {
    Objects.requireNonNull(pkcs12, "pkcs12");
    Objects.requireNonNull(password, "password");
    if (pkcs12.length == 0) {
      throw new CertificateLoadException("The PKCS#12 file is empty");
    }

    char[] workingPassword = password.clone();
    try (ByteArrayInputStream input = new ByteArrayInputStream(pkcs12)) {
      KeyStore keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(input, workingPassword);

      String keyAlias = resolveAlias(keyStore, alias);
      Key key = keyStore.getKey(keyAlias, workingPassword);
      if (!(key instanceof PrivateKey privateKey)) {
        throw new CertificateLoadException("The selected entry does not contain a private key");
      }

      Certificate[] chain = keyStore.getCertificateChain(keyAlias);
      if (chain == null || chain.length == 0) {
        throw new CertificateLoadException("The private key has no certificate chain");
      }

      List<X509Certificate> certificates = new ArrayList<>(chain.length);
      for (Certificate certificate : chain) {
        if (!(certificate instanceof X509Certificate x509Certificate)) {
          throw new CertificateLoadException("The chain contains a non-X.509 certificate");
        }
        certificates.add(x509Certificate);
      }

      return new SigningIdentity(privateKey, certificates.get(0), certificates);
    } catch (GeneralSecurityException | IOException e) {
      throw new CertificateLoadException("Could not load the PKCS#12 certificate", e);
    } finally {
      Arrays.fill(workingPassword, '\0');
    }
  }

  private static String resolveAlias(KeyStore keyStore, String requestedAlias)
      throws GeneralSecurityException {
    if (requestedAlias != null && !requestedAlias.isBlank()) {
      if (!keyStore.containsAlias(requestedAlias) || !keyStore.isKeyEntry(requestedAlias)) {
        throw new CertificateLoadException("The requested certificate alias was not found");
      }
      return requestedAlias;
    }

    List<String> keyAliases = new ArrayList<>();
    for (String alias : Collections.list(keyStore.aliases())) {
      if (keyStore.isKeyEntry(alias)) {
        keyAliases.add(alias);
      }
    }

    if (keyAliases.isEmpty()) {
      throw new CertificateLoadException("The PKCS#12 file does not contain a private key");
    }
    if (keyAliases.size() > 1) {
      throw new CertificateLoadException(
          "The PKCS#12 file contains multiple private keys; configure CERTIFICATE_ALIAS");
    }
    return keyAliases.get(0);
  }
}
