package com.bendigo.quote.domain;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized commission matrix (application.yml -> commission.matrix.*). Single source of truth
 * for per-band pricing; changing rates is a config change, not a code change.
 */
@ConfigurationProperties(prefix = "commission")
public record RiskBandMatrix(Map<String, RateSpec> matrix) {

    public record RateSpec(BigDecimal baseRate, BigDecimal termMultiplier) {}
}
