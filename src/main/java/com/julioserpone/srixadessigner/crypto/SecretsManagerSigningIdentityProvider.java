package com.julioserpone.srixadessigner.crypto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * Loads the PKCS#12 certificate and password from AWS Secrets Manager.
 *
 * <p>Expected secret (SecretString or SecretBinary) is JSON:
 *
 * <pre>{@code
 * {"p12Base64":"...","password":"...","alias":"optional"}
 * }</pre>
 *
 * <p>The secret is cached in memory for a short time. No secret material is logged.
 */
public final class SecretsManagerSigningIdentityProvider implements SigningIdentityProvider {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final SecretsManagerClient client;
  private final String secretId;
  private final String defaultAlias;
  private final Duration cacheTtl;
  private final boolean ownsClient;
  private volatile CachedIdentity cached;

  public SecretsManagerSigningIdentityProvider(
      SecretsManagerClient client, String secretId, String defaultAlias, Duration cacheTtl) {
    this(client, secretId, defaultAlias, cacheTtl, false);
  }

  private SecretsManagerSigningIdentityProvider(
      SecretsManagerClient client,
      String secretId,
      String defaultAlias,
      Duration cacheTtl,
      boolean ownsClient) {
    this.client = Objects.requireNonNull(client, "client");
    if (secretId == null || secretId.isBlank()) {
      throw new IllegalArgumentException("CERTIFICATE_SECRET_ID is required");
    }
    this.secretId = secretId;
    this.defaultAlias = defaultAlias;
    this.cacheTtl = cacheTtl == null || cacheTtl.isNegative() ? Duration.ofMinutes(5) : cacheTtl;
    this.ownsClient = ownsClient;
  }

  public static SecretsManagerSigningIdentityProvider fromEnvironment() {
    String secretId = System.getenv("CERTIFICATE_SECRET_ID");
    String alias = blankToNull(System.getenv("CERTIFICATE_ALIAS"));
    long cacheSeconds = parseLong(System.getenv("CERTIFICATE_CACHE_SECONDS"), 300L);

    SecretsManagerClient client =
        SecretsManagerClient.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    return new SecretsManagerSigningIdentityProvider(
        client, secretId, alias, Duration.ofSeconds(cacheSeconds), true);
  }

  @Override
  public SigningIdentity get() {
    CachedIdentity current = cached;
    if (current != null && !current.isExpired()) {
      return current.identity();
    }

    synchronized (this) {
      current = cached;
      if (current != null && !current.isExpired()) {
        return current.identity();
      }
      CachedIdentity loaded = load();
      cached = loaded;
      return loaded.identity();
    }
  }

  private CachedIdentity load() {
    GetSecretValueResponse response =
        client.getSecretValue(GetSecretValueRequest.builder().secretId(secretId).build());

    String secretJson = response.secretString();
    if (secretJson == null && response.secretBinary() != null) {
      secretJson = StandardCharsets.UTF_8.decode(response.secretBinary().asByteBuffer()).toString();
    }
    if (secretJson == null || secretJson.isBlank()) {
      throw new CertificateLoadException("The configured secret is empty");
    }

    final SecretPayload payload;
    try {
      payload = OBJECT_MAPPER.readValue(secretJson, SecretPayload.class);
    } catch (Exception e) {
      throw new CertificateLoadException("The secret does not have the expected JSON format", e);
    }

    if (payload.p12Base64() == null || payload.p12Base64().isBlank()) {
      throw new CertificateLoadException("The secret does not contain p12Base64");
    }

    byte[] pkcs12;
    try {
      pkcs12 = Base64.getMimeDecoder().decode(payload.p12Base64());
    } catch (IllegalArgumentException e) {
      throw new CertificateLoadException("The secret contains invalid base64", e);
    }

    String alias =
        payload.alias() == null || payload.alias().isBlank() ? defaultAlias : payload.alias();
    char[] password = payload.password() == null ? new char[0] : payload.password().toCharArray();
    try {
      SigningIdentity identity = CertificateLoader.load(pkcs12, password, alias);
      return new CachedIdentity(identity, Instant.now().plus(cacheTtl));
    } finally {
      java.util.Arrays.fill(password, '\0');
      java.util.Arrays.fill(pkcs12, (byte) 0);
    }
  }

  @Override
  public void close() {
    if (ownsClient) {
      client.close();
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static long parseLong(String value, long defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid numeric environment variable: " + value, e);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record SecretPayload(String p12Base64, String password, String alias) {}

  private record CachedIdentity(SigningIdentity identity, Instant expiresAt) {
    boolean isExpired() {
      return Instant.now().isAfter(expiresAt);
    }
  }
}
