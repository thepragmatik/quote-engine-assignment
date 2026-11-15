package com.bendigo.quote.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.resilience.VendorQuotePort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Validates CORS origin restrictions enforced at the backend REST API boundary. */
@WebMvcTest(QuoteController.class)
@DisplayName("CorsSecurityTest — Frontend-to-Backend CORS Origin Restrictions")
class CorsSecurityTest {

  @Autowired MockMvc mockMvc;

  @MockitoBean VendorQuotePort vendorQuotePort;

  @MockitoBean CommissionCalculationStrategy commissionCalculationStrategy;

  @Test
  @DisplayName(
      "Allows preflight CORS requests from authorized frontend origin (http://localhost:3000)")
  void allowsAuthorizedOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/quotes")
                .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
        .andExpect(status().isOk())
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:3000"))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "POST,OPTIONS"));
  }

  @Test
  @DisplayName("Rejects or denies CORS headers for unauthorized external origin")
  void deniesUnauthorizedOrigin() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/quotes")
                .header(HttpHeaders.ORIGIN, "http://malicious-attacker.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type"))
        .andExpect(status().isForbidden());
  }
}
