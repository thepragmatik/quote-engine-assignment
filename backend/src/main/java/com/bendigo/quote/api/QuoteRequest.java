package com.bendigo.quote.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

/**
 * Inbound Data Transfer Object (DTO) for loan commission quote requests.
 *
 * <p>Enforces strict Jakarta Bean Validation rules matching functional specification FR-1.
 * Furthermore, unknown properties are rejected via {@code @JsonIgnoreProperties(ignoreUnknown =
 * false)} to safeguard against JSON injection and parameter tampering attacks.
 *
 * @param loanAmount the principal amount requested; must be between $1,000.00 and $10,000,000.00
 *     inclusive
 * @param loanTermInMonths tenure duration; must be an integer between 6 and 360 months inclusive
 * @param riskBand assessment operational risk tier; must strictly match {@code LOW}, {@code
 *     MEDIUM}, or {@code HIGH}
 */
@JsonIgnoreProperties(
    ignoreUnknown =
        false) // ATTENTION(security): sanitization control — unknown fields are rejected
public record QuoteRequest(
    @NotNull(message = LOAN_AMOUNT_REQUIRED_MSG)
        @DecimalMin(value = MIN_LOAN_AMOUNT_STR, message = LOAN_AMOUNT_RANGE_MSG)
        @DecimalMax(value = MAX_LOAN_AMOUNT_STR, message = LOAN_AMOUNT_RANGE_MSG)
        BigDecimal loanAmount,
    @NotNull(message = LOAN_TERM_REQUIRED_MSG)
        @Min(value = MIN_LOAN_TERM_MONTHS, message = LOAN_TERM_RANGE_MSG)
        @Max(value = MAX_LOAN_TERM_MONTHS, message = LOAN_TERM_RANGE_MSG)
        Integer loanTermInMonths,
    @NotNull(message = RISK_BAND_MSG) @Pattern(regexp = RISK_BANDS_PATTERN, message = RISK_BAND_MSG)
        String riskBand) {

  // Boundary value constants — single source of truth for backend validation limits
  public static final String MIN_LOAN_AMOUNT_STR = "1000";
  public static final String MAX_LOAN_AMOUNT_STR = "10000000";
  public static final long MIN_LOAN_AMOUNT = 1000L;
  public static final long MAX_LOAN_AMOUNT = 10000000L;

  public static final int MIN_LOAN_TERM_MONTHS = 6;
  public static final int MAX_LOAN_TERM_MONTHS = 360;

  public static final String RISK_BANDS_PATTERN = "LOW|MEDIUM|HIGH";

  // Contractual validation error messages (Functional Requirement FR-1)
  public static final String LOAN_AMOUNT_REQUIRED_MSG = "Loan amount is required.";
  public static final String LOAN_AMOUNT_RANGE_MSG =
      "Loan amount must be between $1,000 and $10,000,000.";
  public static final String LOAN_TERM_REQUIRED_MSG = "Loan term is required.";
  public static final String LOAN_TERM_RANGE_MSG = "Loan term must be between 6 and 360 months.";
  public static final String RISK_BAND_MSG = "Please select a valid risk band.";
}
