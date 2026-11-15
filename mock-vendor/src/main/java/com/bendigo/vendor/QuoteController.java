package com.bendigo.vendor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint simulating the third-party partner commission quote verification API.
 *
 * <p>Implements Functional Requirement FR-4:
 *
 * <ul>
 *   <li><b>Authentication:</b> Enforces header-based authentication via {@code x-api-key}. Missing
 *       or incorrect keys are rejected with HTTP 401 Unauthorized in constant time.
 *   <li><b>Fault Simulation:</b> Applies stochastic outcome classification via {@link FaultEngine}.
 *   <li><b>Latency Injection:</b> Simulates configurable network propagation delays before
 *       returning HTTP 504.
 * </ul>
 */
@RestController
public class QuoteController {

  public static final String VENDOR_QUOTES_PATH = "/api/v1/vendor/quotes";
  public static final String API_KEY_HEADER = "x-api-key";
  public static final String VERIFICATION_CODE_PREFIX = "VC-";

  private final FaultEngine faultEngine;
  private final Environment env;

  @Autowired
  public QuoteController(FaultEngine faultEngine, Environment env) {
    this.faultEngine = faultEngine;
    this.env = env;
  }

  /**
   * Verifies partner quote eligibility with stochastic fault simulation.
   *
   * @param apiKey caller's credential extracted from the {@code x-api-key} HTTP header
   * @return {@link ResponseEntity} containing verification metadata or a simulated fault status
   *     code
   */
  @PostMapping(VENDOR_QUOTES_PATH)
  public ResponseEntity<Map<String, Object>> quote(
      @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
    // Security Note: the API key is compared per request, in constant time —
    // a plain String.equals leaks key length/prefix via timing.
    String expectedKey = env.getProperty("vendor.api-key", "");
    byte[] given = apiKey == null ? new byte[0] : apiKey.getBytes(StandardCharsets.UTF_8);
    byte[] expected = expectedKey.getBytes(StandardCharsets.UTF_8);
    if (given.length == 0 || !MessageDigest.isEqual(given, expected)) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));
    }

    double r = faultEngine.random().nextDouble();
    FaultEngine.Outcome outcome = faultEngine.classify(r);
    return switch (outcome) {
      case OK ->
          ResponseEntity.ok(
              Map.of(
                  "verificationCode",
                  VERIFICATION_CODE_PREFIX + UUID.randomUUID().toString().replace("-", ""),
                  "verified",
                  true));
      case ERROR_500 -> ResponseEntity.status(500).build();
      case BAD_REQUEST_400 -> ResponseEntity.status(400).build();
      case LATENCY_504 -> {
        sleepRandomLatency();
        yield ResponseEntity.status(504).build();
      }
    };
  }

  private void sleepRandomLatency() {
    // Configuration Note: defaults live ONLY in application.yml (Rule 2) — no second
    // source of truth here.
    long minMs = Long.parseLong(env.getProperty("vendor.latency-min-ms"));
    long maxMs = Long.parseLong(env.getProperty("vendor.latency-max-ms"));
    long sleepMs = (minMs >= maxMs) ? minMs : faultEngine.random().nextLong(minMs, maxMs);
    try {
      Thread.sleep(sleepMs);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
