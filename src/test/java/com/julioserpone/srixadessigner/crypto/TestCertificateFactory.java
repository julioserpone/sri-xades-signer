package com.julioserpone.srixadessigner.crypto;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/** Generates an ephemeral PKCS#12 for tests. Never use this material outside tests. */
public final class TestCertificateFactory {

  private static final char[] PASSWORD = "changeit".toCharArray();

  static {
    if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
      Security.addProvider(new BouncyCastleProvider());
    }
  }

  private TestCertificateFactory() {}

  public static TestP12 create() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();

      X500Name subject = new X500Name("CN=SRI Test Signer,O=Test,C=EC");
      Date notBefore = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
      Date notAfter = Date.from(Instant.now().plus(3650, ChronoUnit.DAYS));

      JcaX509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              subject,
              BigInteger.valueOf(Instant.now().toEpochMilli()),
              notBefore,
              notAfter,
              subject,
              keyPair.getPublic());
      builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
      builder.addExtension(
          Extension.keyUsage,
          true,
          new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

      ContentSigner signer =
          new JcaContentSignerBuilder("SHA256withRSA")
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .build(keyPair.getPrivate());
      X509Certificate certificate =
          new JcaX509CertificateConverter()
              .setProvider(BouncyCastleProvider.PROVIDER_NAME)
              .getCertificate(builder.build(signer));
      certificate.checkValidity();

      java.security.KeyStore keyStore = java.security.KeyStore.getInstance("PKCS12");
      keyStore.load(null, PASSWORD);
      keyStore.setKeyEntry("test", keyPair.getPrivate(), PASSWORD, new Certificate[] {certificate});
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      keyStore.store(output, PASSWORD);

      return new TestP12(output.toByteArray(), PASSWORD, certificate, keyPair.getPrivate());
    } catch (Exception e) {
      throw new IllegalStateException("Could not create the test certificate", e);
    }
  }

  public record TestP12(
      byte[] pkcs12, char[] password, X509Certificate certificate, PrivateKey privateKey) {}
}
