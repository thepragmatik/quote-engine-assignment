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
    @NotNull(message = "Loan amount is required.")
        @DecimalMin(value = "1000", message = "Loan amount must be between $1,000 and $10,000,000.")
        @DecimalMax(
            value = "10000000",
            message = "Loan amount must be between $1,000 and $10,000,000.")
        BigDecimal loanAmount,
    @NotNull(message = "Loan term is required.")
        @Min(value = 6, message = "Loan term must be between 6 and 360 months.")
        @Max(value = 360, message = "Loan term must be between 6 and 360 months.")
        Integer loanTermInMonths,
    @NotNull(message = "Please select a valid risk band.")
        @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Please select a valid risk band.")
        String riskBand) {}
