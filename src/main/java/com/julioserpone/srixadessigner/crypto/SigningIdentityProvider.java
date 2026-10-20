package com.julioserpone.srixadessigner.crypto;

/** Supplies the signing identity, normally from AWS Secrets Manager. */
public interface SigningIdentityProvider extends AutoCloseable {

  SigningIdentity get();

  @Override
  default void close() {}
}
