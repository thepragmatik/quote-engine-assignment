package com.bendigo.quote.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = false) // ATTENTION(security): sanitization control — unknown fields are rejected
public record QuoteRequest(
        @NotNull
                @DecimalMin(
                        value = "1000",
                        message = "Loan amount must be between $1,000 and $10,000,000.")
                @DecimalMax(
                        value = "10000000",
                        message = "Loan amount must be between $1,000 and $10,000,000.")
                BigDecimal loanAmount,
        @NotNull
                @Min(value = 6, message = "Loan term must be between 6 and 360 months.")
                @Max(value = 360, message = "Loan term must be between 6 and 360 months.")
                Integer loanTermInMonths,
        @NotNull
                @Pattern(regexp = "LOW|MEDIUM|HIGH", message = "Please select a valid risk band.")
                String riskBand) {}
