package com.bendigo.vendor;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.random.RandomGenerator;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * REST integration test suite for {@link QuoteController} asserting authentication enforcement,
 * verification metadata generation, and simulated HTTP fault responses.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("VendorControllerTest — Mock Partner Authentication & Fault Simulation")
class VendorControllerTest {

  /** Set latency min/max to 0ms so the simulated 504 test runs deterministically without delay. */
  @DynamicPropertySource
  static void latencyZero(DynamicPropertyRegistry registry) {
    registry.add("vendor.latency-min-ms", () -> "0");
    registry.add("vendor.latency-max-ms", () -> "0");
  }

  private static final String BODY =
      "{\"loanAmount\":250000,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";

  @Autowired MockMvc mockMvc;

  @MockitoBean FaultEngine faultEngine;

  @BeforeEach
  void setUp() {
    when(faultEngine.random()).thenReturn(RandomGenerator.getDefault());
  }

  private void forced(FaultEngine.Outcome outcome) {
    when(faultEngine.classify(anyDouble())).thenReturn(outcome);
  }

  @Nested
  @DisplayName("Authentication Enforcement (Header x-api-key)")
  class AuthenticationTests {

    @Test
    @DisplayName("Rejects requests with missing x-api-key header with HTTP 401 Unauthorized")
    void missingApiKey_unauthorized() throws Exception {
      forced(FaultEngine.Outcome.OK);
      mockMvc
          .perform(post("/api/v1/vendor/quotes").content(BODY).contentType("application/json"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Unauthorized"));
    }

    @Test
    @DisplayName("Rejects requests with invalid x-api-key header with HTTP 401 Unauthorized")
    void wrongApiKey_unauthorized() throws Exception {
      forced(FaultEngine.Outcome.OK);
      mockMvc
          .perform(
              post("/api/v1/vendor/quotes")
                  .content(BODY)
                  .header("x-api-key", "nope")
                  .contentType("application/json"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("Unauthorized"));
    }
  }

  @Nested
  @DisplayName("Simulation of Configured Partner Outcomes")
  class SimulatedOutcomes {

    @Test
    @DisplayName(
        "Forced OK: returns HTTP 200 with verificationCode starting with 'VC-' and verified=true")
    void forcedOk_returnsVerificationCode() throws Exception {
      forced(FaultEngine.Outcome.OK);
      mockMvc
          .perform(
              post("/api/v1/vendor/quotes")
                  .content(BODY)
                  .header("x-api-key", "dev-vendor-secret")
                  .contentType("application/json"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.verificationCode").value(Matchers.startsWith("VC-")))
          .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    @DisplayName("Forced ERROR: returns HTTP 500 Internal Server Error simulating partner failure")
    void forcedError_returns500() throws Exception {
      forced(FaultEngine.Outcome.ERROR_500);
      mockMvc
          .perform(
              post("/api/v1/vendor/quotes")
                  .content(BODY)
                  .header("x-api-key", "dev-vendor-secret")
                  .contentType("application/json"))
          .andExpect(status().isInternalServerError());
    }

    @Test
    @DisplayName("Forced BAD_REQUEST: returns HTTP 400 Bad Request simulating partner rejection")
    void forcedBadRequest_returns400() throws Exception {
      forced(FaultEngine.Outcome.BAD_REQUEST_400);
      mockMvc
          .perform(
              post("/api/v1/vendor/quotes")
                  .content(BODY)
                  .header("x-api-key", "dev-vendor-secret")
                  .contentType("application/json"))
          .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName(
        "Forced LATENCY: simulates downstream timeout delay and returns HTTP 504 Gateway Timeout")
    void forcedLatency_returns504() throws Exception {
      forced(FaultEngine.Outcome.LATENCY_504);
      mockMvc
          .perform(
              post("/api/v1/vendor/quotes")
                  .content(BODY)
                  .header("x-api-key", "dev-vendor-secret")
                  .contentType("application/json"))
          .andExpect(status().isGatewayTimeout());
    }
  }
}
