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

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> badRequest(Exception ex) {
        String detail = "Malformed or invalid request body.";
        if (ex instanceof MethodArgumentNotValidException validation) {
            detail =
                    validation
                            .getBindingResult()
                            .getFieldErrors()
                            .stream()
                            .findFirst()
                            .map(FieldError::getDefaultMessage)
                            .orElse(detail);
        }
        return ResponseEntity.badRequest().body(Map.of("detail", detail));
    }

    @ExceptionHandler({VendorServiceUnavailableException.class, RetryableVendorException.class})
    ResponseEntity<Map<String, String>> vendorUnavailable(RuntimeException ex) {
        // RetryableVendorException reaches here only when ALL retry attempts are exhausted
        // (fail-after-max-attempts) -> same 502 contract as a hard vendor failure.
        return ResponseEntity.status(502).body(Map.of("detail", "Vendor verification unavailable. Please retry."));
    }

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
