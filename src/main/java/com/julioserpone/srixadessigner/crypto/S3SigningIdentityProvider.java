package com.julioserpone.srixadessigner.crypto;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * Loads each tenant's PKCS#12 from a private S3 bucket.
 *
 * <p>The object key is always {@code tenants/<tenant-uuid>/certificate.p12}. Tenant identifiers are
 * validated as UUIDs, so request data cannot select an arbitrary S3 object.
 *
 * <p>The decoded private key and certificate are cached in memory. A SHA-256 hash of the PKCS#12
 * password is cached with the identity and checked on every request, so a cached identity is never
 * returned for an incorrect password. Passwords and PKCS#12 bytes are never logged.
 */
public final class S3SigningIdentityProvider implements TenantSigningIdentityProvider {

  private static final Pattern UUID_PATTERN =
      Pattern.compile(
          "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
  private static final String DEFAULT_KEY_PREFIX = "tenants/";
  private static final int DEFAULT_MAX_CERTIFICATE_BYTES = 1_048_576;
  private static final int DEFAULT_MAX_CACHE_ENTRIES = 100;

  private final CertificateObjectLoader loader;
  private final String keyPrefix;
  private final String defaultAlias;
  private final Duration cacheTtl;
  private final int maxCertificateBytes;
  private final int maxCacheEntries;
  private final S3Client client;
  private final boolean ownsClient;
  private final Map<String, CachedIdentity> cache;

  public S3SigningIdentityProvider(
      S3Client client,
      String bucket,
      String keyPrefix,
      String defaultAlias,
      Duration cacheTtl,
      int maxCertificateBytes,
      int maxCacheEntries) {
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("CERTIFICATE_S3_BUCKET is required");
    }
    this.client = Objects.requireNonNull(client, "client");
    this.ownsClient = true;
    this.keyPrefix = normalizePrefix(keyPrefix);
    this.defaultAlias = blankToNull(defaultAlias);
    this.cacheTtl = normalizeTtl(cacheTtl);
    this.maxCertificateBytes = requirePositive(maxCertificateBytes, "maxCertificateBytes");
    this.maxCacheEntries = requirePositive(maxCacheEntries, "maxCacheEntries");
    this.loader = key -> readFromS3(this.client, bucket, key, this.maxCertificateBytes);
    this.cache = new LinkedHashMap<>(16, 0.75f, true);
  }

  S3SigningIdentityProvider(
      CertificateObjectLoader loader,
      String keyPrefix,
      String defaultAlias,
      Duration cacheTtl,
      int maxCertificateBytes,
      int maxCacheEntries) {
    this.client = null;
    this.ownsClient = false;
    this.loader = Objects.requireNonNull(loader, "loader");
    this.keyPrefix = normalizePrefix(keyPrefix);
    this.defaultAlias = blankToNull(defaultAlias);
    this.cacheTtl = normalizeTtl(cacheTtl);
    this.maxCertificateBytes = requirePositive(maxCertificateBytes, "maxCertificateBytes");
    this.maxCacheEntries = requirePositive(maxCacheEntries, "maxCacheEntries");
    this.cache = new LinkedHashMap<>(16, 0.75f, true);
  }

  public static S3SigningIdentityProvider fromEnvironment() {
    return fromEnvironment(System.getenv());
  }

  public static S3SigningIdentityProvider fromEnvironment(Map<String, String> environment) {
    String bucket = environment.get("CERTIFICATE_S3_BUCKET");
    if (bucket == null || bucket.isBlank()) {
      throw new IllegalArgumentException("CERTIFICATE_S3_BUCKET is required");
    }
    String prefix = environment.getOrDefault("CERTIFICATE_S3_PREFIX", DEFAULT_KEY_PREFIX);
    String alias = blankToNull(environment.get("CERTIFICATE_ALIAS"));
    long cacheSeconds = parseLong(environment.get("CERTIFICATE_CACHE_SECONDS"), 300L);
    int maxCertificateBytes =
        parseInt(environment.get("MAX_CERTIFICATE_BYTES"), DEFAULT_MAX_CERTIFICATE_BYTES);
    int maxCacheEntries =
        parseInt(environment.get("CERTIFICATE_CACHE_MAX_ENTRIES"), DEFAULT_MAX_CACHE_ENTRIES);

    S3Client client =
        S3Client.builder().httpClientBuilder(UrlConnectionHttpClient.builder()).build();
    try {
      return new S3SigningIdentityProvider(
          client,
          bucket,
          prefix,
          alias,
          Duration.ofSeconds(cacheSeconds),
          maxCertificateBytes,
          maxCacheEntries);
    } catch (RuntimeException e) {
      client.close();
      throw e;
    }
  }

  @Override
  public SigningIdentity get(String tenantUuid, char[] password) {
    Objects.requireNonNull(password, "password");
    String key = tenantKey(keyPrefix, tenantUuid);
    String passwordHash = passwordHash(password);

    synchronized (this) {
      CachedIdentity current = cache.get(key);
      if (current != null && !current.isExpired() && current.matches(passwordHash)) {
        return current.identity();
      }

      byte[] pkcs12 = loader.load(key);
      try {
        if (pkcs12.length > maxCertificateBytes) {
          throw new CertificateLoadException(
              "The PKCS#12 file exceeds the configured maximum size of "
                  + maxCertificateBytes
                  + " bytes");
        }
        SigningIdentity identity;
        try {
          identity = CertificateLoader.load(pkcs12, password, defaultAlias);
        } catch (IllegalArgumentException e) {
          throw new CertificateLoadException("The PKCS#12 identity is not usable for signing", e);
        }
        cache.put(key, new CachedIdentity(identity, passwordHash, Instant.now().plus(cacheTtl)));
        evictOldestIfNeeded();
        return identity;
      } finally {
        Arrays.fill(pkcs12, (byte) 0);
      }
    }
  }

  @Override
  public void close() {
    synchronized (this) {
      cache.clear();
    }
    if (ownsClient && client != null) {
      client.close();
    }
  }

  static String tenantKey(String keyPrefix, String tenantUuid) {
    return normalizePrefix(keyPrefix) + normalizeTenantUuid(tenantUuid) + "/certificate.p12";
  }

  static String normalizeTenantUuid(String tenantUuid) {
    if (tenantUuid == null || !UUID_PATTERN.matcher(tenantUuid).matches()) {
      throw new IllegalArgumentException("tenantUuid must be a UUID");
    }
    return UUID.fromString(tenantUuid).toString();
  }

  private static String normalizePrefix(String prefix) {
    String normalized = prefix == null || prefix.isBlank() ? DEFAULT_KEY_PREFIX : prefix.trim();
    if (!normalized.endsWith("/")) {
      normalized = normalized + "/";
    }
    if (normalized.startsWith("/")) {
      throw new IllegalArgumentException("CERTIFICATE_S3_PREFIX must not start with /");
    }
    return normalized;
  }

  private static Duration normalizeTtl(Duration cacheTtl) {
    if (cacheTtl == null) {
      return Duration.ofMinutes(5);
    }
    if (cacheTtl.isNegative()) {
      throw new IllegalArgumentException("cacheTtl must be zero or positive");
    }
    return cacheTtl;
  }

  private static int requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private void evictOldestIfNeeded() {
    while (cache.size() > maxCacheEntries) {
      var iterator = cache.entrySet().iterator();
      if (!iterator.hasNext()) {
        return;
      }
      iterator.next();
      iterator.remove();
    }
  }

  private static byte[] readFromS3(
      S3Client client, String bucket, String key, int maxCertificateBytes) {
    GetObjectRequest request = GetObjectRequest.builder().bucket(bucket).key(key).build();
    try (ResponseInputStream<GetObjectResponse> input = client.getObject(request)) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[8192];
      int total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxCertificateBytes) {
          throw new CertificateLoadException(
              "The PKCS#12 file exceeds the configured maximum size of "
                  + maxCertificateBytes
                  + " bytes");
        }
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    } catch (NoSuchKeyException e) {
      throw new CertificateLoadException("The tenant certificate was not found in S3", e);
    } catch (S3Exception e) {
      throw new CertificateLoadException("Could not read the tenant certificate from S3", e);
    } catch (IOException e) {
      throw new CertificateLoadException("Could not read the tenant certificate from S3", e);
    }
  }

  private static String passwordHash(char[] password) {
    CharsetEncoder encoder = StandardCharsets.UTF_8.newEncoder();
    ByteBuffer encoded;
    try {
      encoded = encoder.encode(CharBuffer.wrap(password));
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("The password is not valid UTF-8", e);
    }

    byte[] passwordBytes = new byte[encoded.remaining()];
    encoded.get(passwordBytes);
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(passwordBytes));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    } finally {
      Arrays.fill(passwordBytes, (byte) 0);
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
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

  @FunctionalInterface
  interface CertificateObjectLoader {
    byte[] load(String objectKey);
  }

  private record CachedIdentity(SigningIdentity identity, String passwordHash, Instant expiresAt) {

    boolean isExpired() {
      return !Instant.now().isBefore(expiresAt);
    }

    boolean matches(String candidateHash) {
      return MessageDigest.isEqual(
          passwordHash.getBytes(StandardCharsets.US_ASCII),
          candidateHash.getBytes(StandardCharsets.US_ASCII));
    }
  }
}
