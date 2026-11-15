package com.bendigo.quote.domain;

import java.math.BigDecimal;

/**
 * Strategy interface encapsulating tier-based commission calculation logic.
 *
 * <p>Applying the <b>Strategy Pattern</b> decouples the commission computation algorithm from the
 * web controller and HTTP orchestration layer. Alternative pricing strategies (e.g. promotional
 * flat-rate pricing, tiered step-down rates, or remote rule engine adapters) can be substituted by
 * providing a different bean implementation without modifying client code.
 */
public interface CommissionCalculationStrategy {

  /**
   * Computes the commission quote for a given loan request.
   *
   * @param loanAmount the principal loan amount requested (in standard currency units)
   * @param termMonths duration of the loan in months
   * @param riskBand the client risk profile tier (e.g. {@code LOW}, {@code MEDIUM}, {@code HIGH})
   * @return a {@link Quote} record containing the quantized rate and total payout
   * @throws InvalidLoanParameterException if the risk band is unknown or parameters violate domain
   *     rules
   */
  Quote calculate(BigDecimal loanAmount, int termMonths, String riskBand);

  /**
   * Immutable value carrier representing the outcome of a commission calculation.
   *
   * @param commissionRate the computed commission rate as a 4-decimal fraction (e.g. {@code 0.0231}
   *     for 2.3100%)
   * @param totalCommission the final payout rounded to 2 decimal places using Banker's Rounding
   *     (e.g. {@code 5775.00})
   */
  record Quote(BigDecimal commissionRate, BigDecimal totalCommission) {}
}
