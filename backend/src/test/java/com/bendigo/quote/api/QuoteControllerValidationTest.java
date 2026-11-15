package com.bendigo.quote.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.resilience.VendorQuotePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contractual validation test suite enforcing Functional Requirement FR-1 constraints and security
 * sanitization controls at the REST API boundary.
 */
@WebMvcTest(QuoteController.class)
@DisplayName("QuoteControllerValidationTest — HTTP 400 Validation & Sanitization Boundaries")
class QuoteControllerValidationTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean VendorQuotePort vendorQuotePort;

  @MockitoBean CommissionCalculationStrategy commissionCalculationStrategy;

  private static final MediaType JSON = MediaType.APPLICATION_JSON;

  @Nested
  @DisplayName("Loan Amount Boundary Enforcement (FR-1: $1,000 to $10,000,000)")
  class LoanAmountBoundaries {

    @Test
    @DisplayName("Rejects loan amount of $999.99 with exact FR-1 validation message")
    void loanAmountBelowMinimumIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":999.99,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.detail").value("Loan amount must be between $1,000 and $10,000,000."));
    }

    @Test
    @DisplayName("Rejects loan amount of $10,000,000.01 with exact FR-1 validation message")
    void loanAmountAboveMaximumIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":10000000.01,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.detail").value("Loan amount must be between $1,000 and $10,000,000."));
    }

    @Test
    @DisplayName("Rejects missing or null loan amount")
    void nullLoanAmountIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content("{\"loanAmount\":null,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").exists());
    }
  }

  @Nested
  @DisplayName("Loan Term Boundary Enforcement (FR-1: 6 to 360 months)")
  class LoanTermBoundaries {

    @Test
    @DisplayName("Rejects loan tenure of 5 months with exact FR-1 validation message")
    void termBelowMinimumIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":250000.00,\"loanTermInMonths\":5,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Loan term must be between 6 and 360 months."));
    }

    @Test
    @DisplayName("Rejects loan tenure of 361 months with exact FR-1 validation message")
    void termAboveMaximumIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":250000.00,\"loanTermInMonths\":361,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Loan term must be between 6 and 360 months."));
    }

    @Test
    @DisplayName("Rejects missing or null loan term")
    void nullLoanTermIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":250000.00,\"loanTermInMonths\":null,\"riskBand\":\"MEDIUM\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").exists());
    }
  }

  @Nested
  @DisplayName("Risk Band Tier Enforcement (FR-1: LOW, MEDIUM, HIGH)")
  class RiskBandValidation {

    @Test
    @DisplayName("Rejects unlisted risk tier 'EXTREME' with exact FR-1 validation message")
    void unknownRiskBandIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"EXTREME\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Please select a valid risk band."));
    }

    @Test
    @DisplayName("Rejects missing or null risk band")
    void nullRiskBandIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content("{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":null}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").value("Please select a valid risk band."));
    }
  }

  @Nested
  @DisplayName("Security & Anti-Tampering Controls (Unknown Properties & JSON Integrity)")
  class SecuritySanitization {

    @Test
    @DisplayName("Rejects unknown properties (parameter tampering protection)")
    void unknownExtraFieldIsRejected() throws Exception {
      mockMvc
          .perform(
              post("/api/v1/quotes")
                  .contentType(JSON)
                  .content(
                      "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\",\"sneakyFee\":1}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("Rejects malformed JSON payloads")
    void malformedJsonIsRejected() throws Exception {
      mockMvc
          .perform(post("/api/v1/quotes").contentType(JSON).content("{not a valid json!"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.detail").exists());
    }
  }
}
