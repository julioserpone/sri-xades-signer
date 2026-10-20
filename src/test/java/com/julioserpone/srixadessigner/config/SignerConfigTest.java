package com.julioserpone.srixadessigner.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SignerConfigTest {

  @Test
  void usesSafeDefaults() {
    SignerConfig config = SignerConfig.fromEnvironment(Map.of());

    assertEquals(1_048_576, config.maxXmlBytes());
    assertEquals(SignerConfig.DEFAULT_ALLOWED_ROOTS, config.allowedRoots());
    assertTrue(config.verifyAfterSign());
    assertTrue(config.requireCertificateValidity());
    assertEquals(Duration.ofMinutes(5), config.certificateCacheTtl());
  }

  @Test
  void parsesEnvironment() {
    SignerConfig config =
        SignerConfig.fromEnvironment(
            Map.of(
                "MAX_XML_BYTES", "2048",
                "ALLOWED_ROOTS", "factura, notaCredito",
                "VERIFY_AFTER_SIGN", "false",
                "REQUIRE_CERTIFICATE_VALIDITY", "false",
                "CERTIFICATE_ALIAS", "prod",
                "CERTIFICATE_CACHE_SECONDS", "60"));

    assertEquals(2048, config.maxXmlBytes());
    assertEquals(Set.of("factura", "notaCredito"), config.allowedRoots());
    assertFalse(config.verifyAfterSign());
    assertFalse(config.requireCertificateValidity());
    assertEquals("prod", config.certificateAlias());
    assertEquals(Duration.ofSeconds(60), config.certificateCacheTtl());
  }

  @Test
  void rejectsInvalidBooleans() {
    assertThrows(
        IllegalArgumentException.class,
        () -> SignerConfig.fromEnvironment(Map.of("VERIFY_AFTER_SIGN", "sometimes")));
  }
}
