package com.julioserpone.srixadessigner;

/** The request is not a well-formed, allowed XML document. */
public class InvalidXmlException extends RuntimeException {

  public InvalidXmlException(String message) {
    super(message);
  }

  public InvalidXmlException(String message, Throwable cause) {
    super(message, cause);
  }
}
