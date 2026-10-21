package com.julioserpone.srixadessigner.crypto;

/**
 * Supplies a signing identity for a specific tenant.
 *
 * <p>The password is used only to open the tenant PKCS#12. Implementations must not retain the
 * password array; callers should clear it after use.
 */
public interface TenantSigningIdentityProvider extends AutoCloseable {

  /**
   * Loads or returns the signing identity for a tenant.
   *
   * @param tenantUuid tenant identifier, formatted as a UUID
   * @param password password for the tenant PKCS#12
   * @return a validated signing identity
   */
  SigningIdentity get(String tenantUuid, char[] password);

  @Override
  default void close() {}
}
