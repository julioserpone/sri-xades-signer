package com.julioserpone.srixadessigner;

/** Base exception for signing failures. */
public class SigningException extends RuntimeException {

  public SigningException(String message) {
    super(message);
  }

  public SigningException(String message, Throwable cause) {
    super(message, cause);
  }
}
