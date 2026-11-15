package com.bendigo.quote.api;

import com.bendigo.quote.resilience.CircuitOpenException;
import com.bendigo.quote.resilience.RetryableVendorException;
import com.bendigo.quote.resilience.VendorServiceUnavailableException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralized REST exception handler ensuring uniform error contract envelopes across all
 * endpoints.
 *
 * <p>Translates domain and infrastructure exceptions into structured HTTP response bodies matching
 * the OpenAPI specifications:
 *
 * <ul>
 *   <li>{@code 400 Bad Request}: Client validation failures and malformed request bodies.
 *   <li>{@code 502 Bad Gateway}: Upstream partner vendor failures or exhausted retry attempts.
 *   <li>{@code 503 Service Unavailable}: Circuit breaker OPEN state signaling downstream
 *       degradation.
 * </ul>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Handles validation constraints and unreadable HTTP message exceptions (e.g. malformed JSON or
   * unknown properties).
   */
  @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
  ResponseEntity<Map<String, String>> badRequest(Exception ex) {
    String detail = "Malformed or invalid request body.";
    if (ex instanceof MethodArgumentNotValidException validation) {
      detail =
          validation.getBindingResult().getFieldErrors().stream()
              .findFirst()
              .map(FieldError::getDefaultMessage)
              .orElse(detail);
    }
    return ResponseEntity.badRequest().body(Map.of("detail", detail));
  }

  /**
   * Handles vendor connectivity failures, non-retryable vendor errors, and exhausted retry
   * attempts.
   */
  @ExceptionHandler({VendorServiceUnavailableException.class, RetryableVendorException.class})
  ResponseEntity<Map<String, String>> vendorUnavailable(RuntimeException ex) {
    // RetryableVendorException reaches here only when all retry attempts have been exhausted
    // (fail-after-max-attempts = true), fulfilling the HTTP 502 Bad Gateway contract.
    return ResponseEntity.status(502)
        .body(Map.of("detail", "Vendor verification unavailable. Please retry."));
  }

  /**
   * Handles circuit breaker OPEN state rejections, providing clear user guidance per specification
   * Section 5.
   */
  @ExceptionHandler(CircuitOpenException.class)
  ResponseEntity<Map<String, String>> circuitOpen(CircuitOpenException ex) {
    return ResponseEntity.status(503)
        .body(
            Map.of(
                "detail",
                "The third-party quote service is currently experiencing high error rates."
                    + " Retries are temporarily paused. Please try again shortly.",
                "state",
                "CIRCUIT_OPEN"));
  }
}
