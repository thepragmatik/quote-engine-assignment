package com.bendigo.quote.resilience;

/** Raised by the circuit-breaker fallback when the vendor circuit is OPEN — maps to HTTP 503. */
public class CircuitOpenException extends RuntimeException {

    public CircuitOpenException(String message) {
        super(message);
    }
}
