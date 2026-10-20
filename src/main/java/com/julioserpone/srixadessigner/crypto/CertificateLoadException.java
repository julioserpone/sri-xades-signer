package com.julioserpone.srixadessigner.crypto;

/** The PKCS#12 certificate or private key could not be loaded safely. */
public class CertificateLoadException extends RuntimeException {

  public CertificateLoadException(String message) {
    super(message);
  }

  public CertificateLoadException(String message, Throwable cause) {
    super(message, cause);
  }
}
