package com.julioserpone.srixadessigner.crypto;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A private key and its X.509 certificate chain.
 *
 * <p>The chain always starts with the signing certificate. The key must be RSA and must match the
 * certificate public key. This avoids producing a signature that cannot be validated.
 */
public record SigningIdentity(
    PrivateKey privateKey, X509Certificate certificate, List<X509Certificate> certificateChain) {

  public SigningIdentity {
    Objects.requireNonNull(privateKey, "privateKey");
    Objects.requireNonNull(certificate, "certificate");

    if (!"RSA".equalsIgnoreCase(privateKey.getAlgorithm())) {
      throw new IllegalArgumentException("Only RSA private keys are supported");
    }
    if (!"RSA".equalsIgnoreCase(certificate.getPublicKey().getAlgorithm())) {
      throw new IllegalArgumentException("The certificate public key is not RSA");
    }
    if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)
        || !(certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey)
        || !rsaPrivateKey.getModulus().equals(rsaPublicKey.getModulus())) {
      throw new IllegalArgumentException("The private key does not match the certificate");
    }

    List<X509Certificate> normalizedChain = new ArrayList<>();
    normalizedChain.add(certificate);
    if (certificateChain != null) {
      for (X509Certificate chainCertificate : certificateChain) {
        if (chainCertificate != null && !chainCertificate.equals(certificate)) {
          normalizedChain.add(chainCertificate);
        }
      }
    }
    certificateChain = List.copyOf(normalizedChain);
  }
}
