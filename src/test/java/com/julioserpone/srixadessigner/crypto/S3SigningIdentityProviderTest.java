package com.julioserpone.srixadessigner.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class S3SigningIdentityProviderTest {

  @Test
  void buildsAKeyFromACanonicalUuid() {
    assertEquals(
        "tenants/123e4567-e89b-12d3-a456-426614174000/certificate.p12",
        S3SigningIdentityProvider.tenantKey("tenants/", "123E4567-E89B-12D3-A456-426614174000"));
  }

  @Test
  void rejectsUuidTraversalAndArbitraryKeys() {
    assertThrows(
        IllegalArgumentException.class,
        () -> S3SigningIdentityProvider.tenantKey("tenants/", "../other"));
    assertThrows(
        IllegalArgumentException.class,
        () -> S3SigningIdentityProvider.tenantKey("tenants/", "not-a-uuid"));
    assertThrows(
        IllegalArgumentException.class,
        () -> S3SigningIdentityProvider.tenantKey("/tenants/", UUID.randomUUID().toString()));
  }

  @Test
  void cachesIdentityOnlyForTheSamePassword() {
    TestCertificateFactory.TestP12 p12 = TestCertificateFactory.create();
    AtomicInteger loads = new AtomicInteger();
    S3SigningIdentityProvider provider =
        provider(p12.pkcs12(), loads, Duration.ofMinutes(5), p12.pkcs12().length, 10);
    String tenant = UUID.randomUUID().toString();

    provider.get(tenant, p12.password());
    provider.get(tenant, p12.password());
    assertEquals(1, loads.get());

    assertThrows(
        CertificateLoadException.class, () -> provider.get(tenant, "wrong-password".toCharArray()));
    assertEquals(2, loads.get());

    // The failed attempt does not evict the identity cached for the correct password.
    provider.get(tenant, p12.password());
    assertEquals(2, loads.get());
  }

  @Test
  void aZeroTtlDisablesTheCache() {
    TestCertificateFactory.TestP12 p12 = TestCertificateFactory.create();
    AtomicInteger loads = new AtomicInteger();
    S3SigningIdentityProvider provider =
        provider(p12.pkcs12(), loads, Duration.ZERO, p12.pkcs12().length, 10);
    String tenant = UUID.randomUUID().toString();

    provider.get(tenant, p12.password());
    provider.get(tenant, p12.password());

    assertEquals(2, loads.get());
  }

  @Test
  void evictsTheLeastRecentlyUsedTenant() {
    TestCertificateFactory.TestP12 p12 = TestCertificateFactory.create();
    AtomicInteger loads = new AtomicInteger();
    S3SigningIdentityProvider provider =
        provider(p12.pkcs12(), loads, Duration.ofMinutes(5), p12.pkcs12().length, 1);
    String first = UUID.randomUUID().toString();
    String second = UUID.randomUUID().toString();

    provider.get(first, p12.password());
    provider.get(second, p12.password());
    provider.get(first, p12.password());

    assertEquals(3, loads.get());
  }

  @Test
  void rejectsCertificatesLargerThanTheConfiguredLimit() {
    TestCertificateFactory.TestP12 p12 = TestCertificateFactory.create();
    S3SigningIdentityProvider provider =
        provider(p12.pkcs12(), new AtomicInteger(), Duration.ofMinutes(5), 10, 10);

    assertThrows(
        CertificateLoadException.class,
        () -> provider.get(UUID.randomUUID().toString(), p12.password()));
  }

  @Test
  void rejectsInvalidTenantBeforeLoadingAnything() {
    AtomicInteger loads = new AtomicInteger();
    S3SigningIdentityProvider provider =
        provider(new byte[1], loads, Duration.ofMinutes(5), 1024, 10);

    assertThrows(
        IllegalArgumentException.class, () -> provider.get("../tenant", "changeit".toCharArray()));
    assertEquals(0, loads.get());
  }

  private static S3SigningIdentityProvider provider(
      byte[] pkcs12,
      AtomicInteger loads,
      Duration ttl,
      int maxCertificateBytes,
      int maxCacheEntries) {
    return new S3SigningIdentityProvider(
        key -> {
          loads.incrementAndGet();
          return Arrays.copyOf(pkcs12, pkcs12.length);
        },
        "tenants/",
        null,
        ttl,
        maxCertificateBytes,
        maxCacheEntries);
  }
}
