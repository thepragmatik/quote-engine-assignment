package com.bendigo.quote.api;

import java.math.BigDecimal;

/**
 * Outbound Data Transfer Object (DTO) returning calculated commission quote details.
 *
 * <p>ATTENTION(money): Monetary values and percentage rates are represented as {@link BigDecimal}
 * directly to preserve Banker's Rounding precision without IEEE 754 binary floating-point
 * distortion.
 *
 * @param quoteId unique transaction identifier (UUID v4) generated per successful calculation
 * @param commissionRate computed commission rate formatted as an unscaled decimal fraction (e.g.
 *     {@code 0.0231} for 2.3100%)
 * @param totalCommission total commission payout amount rounded to 2 decimal places (e.g. {@code
 *     5775.00})
 */
public record QuoteResponse(
    String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {}
