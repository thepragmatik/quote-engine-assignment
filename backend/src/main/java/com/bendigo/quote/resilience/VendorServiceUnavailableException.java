package com.bendigo.quote.resilience;

/** Non-retryable vendor fault (400/401) or vendor reporting the quote as unverified. */
public class VendorServiceUnavailableException extends RuntimeException {

  public VendorServiceUnavailableException(String message) {
    super(message);
  }

  public VendorServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
