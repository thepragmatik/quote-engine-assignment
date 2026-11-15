package com.bendigo.vendor;

import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    private final FaultEngine faultEngine;
    private final Environment env;

    @Autowired
    public QuoteController(FaultEngine faultEngine, Environment env) {
        this.faultEngine = faultEngine;
        this.env = env;
    }

    @PostMapping("/api/v1/vendor/quotes")
    public ResponseEntity<Map<String, Object>> quote(
            @RequestHeader(value = "x-api-key", required = false) String apiKey) {
        // ATTENTION(security): the API key is compared per request.
        String expectedKey = env.getProperty("vendor.api-key");
        if (apiKey == null || !apiKey.equals(expectedKey)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("detail", "Unauthorized"));
        }

        double r = faultEngine.random().nextDouble();
        FaultEngine.Outcome outcome = faultEngine.classify(r);
        return switch (outcome) {
            case OK -> ResponseEntity.ok(Map.of(
                    "verificationCode", "VC-" + UUID.randomUUID().toString().replace("-", ""),
                    "verified", true));
            case ERROR_500 -> ResponseEntity.status(500).build();
            case BAD_REQUEST_400 -> ResponseEntity.status(400).build();
            case LATENCY_504 -> {
                sleepRandomLatency();
                yield ResponseEntity.status(504).build();
            }
        };
    }

    private void sleepRandomLatency() {
        long minMs = Long.parseLong(env.getProperty("vendor.latency-min-ms", "100"));
        long maxMs = Long.parseLong(env.getProperty("vendor.latency-max-ms", "2000"));
        long sleepMs = (minMs >= maxMs) ? minMs : faultEngine.random().nextLong(minMs, maxMs);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
