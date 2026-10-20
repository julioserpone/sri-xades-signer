package com.julioserpone.srixadessigner;

/** The request exceeds the configured maximum payload size. */
public class PayloadTooLargeException extends RuntimeException {

  public PayloadTooLargeException(String message) {
    super(message);
  }
}
