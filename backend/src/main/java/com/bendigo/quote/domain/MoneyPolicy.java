package com.bendigo.quote.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Authoritative financial precision and rounding policy for the commission quote system.
 *
 * <p>Financial integrity requires strict adherence to arbitrary-precision decimal arithmetic.
 * Standard binary floating-point types ({@code float}, {@code double}) introduce binary
 * representation anomalies (such as {@code 0.1 + 0.2 != 0.3}) that aggregate into unacceptable
 * discrepancies in monetary accounting.
 *
 * <p>This utility acts as the sole rounding seam in the domain model. All operations employ
 * <b>Banker's Rounding</b> ({@link RoundingMode#HALF_EVEN}), which rounds towards the nearest
 * neighbor unless both neighbors are equidistant, in which case it rounds towards the even
 * neighbor. This eliminates upward statistical bias across large calculation volumes.
 */
public final class MoneyPolicy {

  private static final int RATE_SCALE = 4;
  private static final int CURRENCY_SCALE = 2;

  private MoneyPolicy() {}

  /**
   * Quantizes a commission rate fraction to 4 decimal places using Banker's Rounding (HALF_EVEN).
   *
   * <p>Precision Note: Rate quantization occurs prior to monetary multiplication to guarantee
   * deterministic fee computation across disparate platforms and client display layers.
   *
   * @param value unscaled rate fraction
   * @return rate quantized to 4 decimal places (e.g. {@code 0.0231})
   */
  public static BigDecimal roundRate4(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException("Rate value to round must not be null");
    }
    return value.setScale(RATE_SCALE, RoundingMode.HALF_EVEN);
  }

  /**
   * Rounds a monetary payout amount to standard currency resolution (2 decimal places / cents)
   * using Banker's Rounding (HALF_EVEN).
   *
   * <p>Precision Note: All currency totals must pass through this method.
   *
   * @param value unscaled monetary amount
   * @return currency amount rounded to 2 decimal places (e.g. {@code 5775.00})
   */
  public static BigDecimal roundMoney2(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException("Monetary value to round must not be null");
    }
    return value.setScale(CURRENCY_SCALE, RoundingMode.HALF_EVEN);
  }
}
