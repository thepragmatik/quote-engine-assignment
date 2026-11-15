package com.bendigo.quote.domain;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalized configuration model for the risk-band commission matrix.
 *
 * <p>Binds directly to {@code commission.matrix.*} in {@code application.yml}, providing a single
 * source of truth for rate calculation parameters. Any operational adjustment to base rates or term
 * multipliers requires only a configuration change, preventing business rule hardcoding in compiled
 * code.
 *
 * @param matrix map of operational risk tiers (e.g. {@code LOW}, {@code MEDIUM}, {@code HIGH}) to
 *     their respective rate specifications
 */
@ConfigurationProperties(prefix = "commission")
public record RiskBandMatrix(Map<String, RateSpec> matrix) {

  /**
   * Rate parameters for a specific risk band.
   *
   * @param baseRate the starting commission rate as a decimal fraction (e.g. {@code 0.0150} for
   *     1.50%)
   * @param termMultiplier the annualized tenure factor applied per year of loan duration (e.g.
   *     {@code 0.0001})
   */
  public record RateSpec(BigDecimal baseRate, BigDecimal termMultiplier) {}
}
