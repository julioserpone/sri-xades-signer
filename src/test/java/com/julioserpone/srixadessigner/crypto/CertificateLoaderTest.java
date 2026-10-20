package com.julioserpone.srixadessigner.crypto;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.julioserpone.srixadessigner.crypto.TestCertificateFactory.TestP12;
import org.junit.jupiter.api.Test;

class CertificateLoaderTest {

  @Test
  void loadsTheOnlyPrivateKey() {
    TestP12 p12 = TestCertificateFactory.create();

    SigningIdentity identity = CertificateLoader.load(p12.pkcs12(), p12.password(), null);

    assertEquals(p12.certificate(), identity.certificate());
    assertEquals(1, identity.certificateChain().size());
    assertArrayEquals(
        p12.certificate().getPublicKey().getEncoded(),
        identity.certificate().getPublicKey().getEncoded());
  }

  @Test
  void loadsByAlias() {
    TestP12 p12 = TestCertificateFactory.create();

    SigningIdentity identity = CertificateLoader.load(p12.pkcs12(), p12.password(), "test");

    assertEquals(p12.certificate(), identity.certificate());
  }

  @Test
  void rejectsWrongPassword() {
    TestP12 p12 = TestCertificateFactory.create();

    assertThrows(
        CertificateLoadException.class,
        () -> CertificateLoader.load(p12.pkcs12(), "wrong-password".toCharArray(), null));
  }

  @Test
  void rejectsUnknownAlias() {
    TestP12 p12 = TestCertificateFactory.create();

    assertThrows(
        CertificateLoadException.class,
        () -> CertificateLoader.load(p12.pkcs12(), p12.password(), "missing"));
  }
}
