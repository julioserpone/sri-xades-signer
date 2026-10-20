package com.julioserpone.srixadessigner.config;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Runtime configuration with safe defaults. */
public record SignerConfig(
    int maxXmlBytes,
    Set<String> allowedRoots,
    boolean verifyAfterSign,
    boolean requireCertificateValidity,
    String certificateAlias,
    Duration certificateCacheTtl) {

  public static final Set<String> DEFAULT_ALLOWED_ROOTS =
      Set.of(
          "factura",
          "notaCredito",
          "notaDebito",
          "comprobanteRetencion",
          "guiaRemision",
          "liquidacionCompra");

  public SignerConfig {
    if (maxXmlBytes <= 0) {
      throw new IllegalArgumentException("maxXmlBytes must be positive");
    }
    if (allowedRoots == null || allowedRoots.isEmpty()) {
      throw new IllegalArgumentException("allowedRoots cannot be empty");
    }
    allowedRoots = Set.copyOf(allowedRoots);
    if (certificateCacheTtl == null || certificateCacheTtl.isNegative()) {
      throw new IllegalArgumentException("certificateCacheTtl must be zero or positive");
    }
  }

  public static SignerConfig defaults() {
    return new SignerConfig(
        1_048_576, DEFAULT_ALLOWED_ROOTS, true, true, null, Duration.ofMinutes(5));
  }

  public static SignerConfig fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static SignerConfig fromEnvironment(Map<String, String> environment) {
    int maxBytes = parseInt(environment.get("MAX_XML_BYTES"), 1_048_576);
    Set<String> roots = parseRoots(environment.get("ALLOWED_ROOTS"));
    boolean verifyAfterSign = parseBoolean(environment.get("VERIFY_AFTER_SIGN"), true);
    boolean requireCertificateValidity =
        parseBoolean(environment.get("REQUIRE_CERTIFICATE_VALIDITY"), true);
    String alias = blankToNull(environment.get("CERTIFICATE_ALIAS"));
    long cacheSeconds = parseLong(environment.get("CERTIFICATE_CACHE_SECONDS"), 300L);

    return new SignerConfig(
        maxBytes,
        roots,
        verifyAfterSign,
        requireCertificateValidity,
        alias,
        Duration.ofSeconds(cacheSeconds));
  }

  private static Set<String> parseRoots(String value) {
    if (value == null || value.isBlank()) {
      return DEFAULT_ALLOWED_ROOTS;
    }
    Set<String> roots = new LinkedHashSet<>();
    for (String part : value.split(",")) {
      String root = part.trim();
      if (!root.isEmpty()) {
        roots.add(root);
      }
    }
    if (roots.isEmpty()) {
      throw new IllegalArgumentException("ALLOWED_ROOTS does not contain any element");
    }
    return roots;
  }

  private static int parseInt(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid integer environment variable: " + value, e);
    }
  }

  private static long parseLong(String value, long defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid long environment variable: " + value, e);
    }
  }

  private static boolean parseBoolean(String value, boolean defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException("Invalid boolean environment variable: " + value);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
