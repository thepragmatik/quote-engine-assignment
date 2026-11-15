package com.bendigo.quote.resilience;

/** Transient vendor fault (HTTP 500/504, timeout, network error) — eligible for retry. */
public class RetryableVendorException extends RuntimeException {

    public RetryableVendorException(String message) {
        super(message);
    }

    public RetryableVendorException(String message, Throwable cause) {
        super(message, cause);
    }
}
