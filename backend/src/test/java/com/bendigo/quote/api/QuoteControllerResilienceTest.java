package com.bendigo.quote.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.quote.resilience.VendorQuotePort;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Integration test suite asserting the fault-tolerance guarantees and Resilience4j circuit state
 * transitions specified in Section 4 of the architecture specification.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName(
    "QuoteControllerResilienceTest — CircuitBreaker, Retry & TimeLimiter State Transitions")
class QuoteControllerResilienceTest {

  private static final String PATH = VendorQuotePort.VERIFY_PATH;
  private static final String VALID_REQUEST =
      "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";
  private static final String VENDOR_OK_BODY =
      "{\"verificationCode\":\"VC-abc\",\"verified\":true}";

  static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Autowired MockMvc mockMvc;

  @Autowired CircuitBreakerRegistry circuitBreakerRegistry;

  @DynamicPropertySource
  static void vendorProps(DynamicPropertyRegistry registry) {
    registry.add("vendor.api-url", () -> wm.baseUrl());
  }

  @BeforeAll
  static void startWireMock() {
    wm.start();
    WireMock.configureFor(wm.port());
  }

  @AfterAll
  static void stopWireMock() {
    wm.stop();
  }

  @BeforeEach
  void resetState() {
    circuitBreakerRegistry.circuitBreaker("vendor").reset();
    wm.resetAll();
    stubVendor(500);
  }

  private static void stubVendor(int status) {
    stubFor(WireMock.post(urlEqualTo(PATH)).willReturn(aResponse().withStatus(status)));
  }

  private static void stubVendorOk() {
    stubFor(
        WireMock.post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(VENDOR_OK_BODY)));
  }

  private MvcResult postQuote() throws Exception {
    return mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andReturn();
  }

  private int statusOf(MvcResult result) {
    return result.getResponse().getStatus();
  }

  @Test
  @DisplayName(
      "Exhausted retries on HTTP 500: makes exactly 3 attempts and maps to HTTP 502 Bad Gateway")
  void threeConsecutiveVendor500sExhaustRetryAndReturn502() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
    verify(3, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName(
      "Non-transient HTTP 401 Unauthorized fails fast without retry (exactly 1 vendor attempt)")
  void vendor401IsNotRetried() throws Exception {
    wm.resetAll();
    stubVendor(401);
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
    verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName(
      "Non-transient HTTP 400 Bad Request fails fast without retry (exactly 1 vendor attempt)")
  void vendor400IsNotRetried() throws Exception {
    wm.resetAll();
    stubVendor(400);
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
    verify(1, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName(
      "CircuitBreaker transitions to OPEN after 5 failed logical requests (15 vendor attempts) and returns HTTP 503")
  void circuitOpensAfterFiveConsecutiveFailingCalls() throws Exception {
    // CircuitBreaker sits OUTSIDE Retry, so it records ONE logical call per request:
    // calls 1-5 exhaust retries (3 HTTP attempts each -> 502); the 5th recorded failure
    // trips the circuit (minimum-number-of-calls=5, 100% failure >= 50% threshold) and
    // every subsequent call short-circuits to 503 with NO vendor traffic at all.
    var statuses = new java.util.ArrayList<Integer>();
    for (int i = 0; i < 5; i++) {
      statuses.add(statusOf(postQuote()));
      assertThat(statuses.get(i)).isEqualTo(502);
    }

    assertThat(statusOf(postQuote())).isEqualTo(503);
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isServiceUnavailable())
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "The third-party quote service is currently experiencing high error"
                        + " rates. Retries are temporarily paused. Please try again"
                        + " shortly."))
        .andExpect(jsonPath("$.state").value("CIRCUIT_OPEN"));

    // 5 logical calls x 3 retry attempts = 15 vendor HTTP calls; nothing after the trip.
    verify(15, postRequestedFor(urlEqualTo(PATH)));
    int callsAfterSix = wm.getAllServeEvents().size();
    assertThat(callsAfterSix).isEqualTo(15);
  }

  @Test
  @DisplayName(
      "CircuitBreaker state transition: OPEN -> HALF_OPEN (after 10s wait / 100ms test) -> CLOSED upon probe success")
  void circuitRecoversThroughHalfOpenProbe() throws Exception {
    for (int i = 0; i < 5; i++) {
      assertThat(statusOf(postQuote())).isEqualTo(502);
    }
    assertThat(statusOf(postQuote())).isEqualTo(503);

    // Circuit is OPEN now; allow the configured wait duration to elapse (100ms in
    // application-test.yml)
    Thread.sleep(300);
    wm.resetAll();
    stubVendorOk();

    // The next call acts as the HALF_OPEN trial probe. When it succeeds, circuit transitions back
    // to CLOSED.
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isOk());

    // Subsequent call also succeeds normally without probe gating -> circuit is fully CLOSED
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName(
      "Non-transient HTTP 401s do NOT count as circuit failures (ignore-exceptions contract prevents false OPEN)")
  void nonTransientFaultsDoNotTripCircuitBreaker() throws Exception {
    wm.resetAll();
    stubVendor(401);

    // Send 5 consecutive 401 requests
    for (int i = 0; i < 5; i++) {
      assertThat(statusOf(postQuote())).isEqualTo(502);
    }

    // Because VendorServiceUnavailableException is in ignore-exceptions, the circuit breaker
    // remains CLOSED.
    // The 6th request is still dispatched to the vendor (returns 502, NOT 503 CIRCUIT_OPEN).
    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway());

    // Verify that all 6 calls were dispatched to the vendor (1 attempt each, no retries)
    verify(6, postRequestedFor(urlEqualTo(PATH)));
  }

  @Test
  @DisplayName(
      "TimeLimiter deadline exceeded (>2000ms): raw HTTP read timeout interrupts request and yields HTTP 502")
  void vendorLatencyBeyondTimeLimiterYields502() throws Exception {
    wm.resetAll();
    stubFor(
        WireMock.post(urlEqualTo(PATH))
            .willReturn(aResponse().withFixedDelay(2100).withStatus(200)));

    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
  }
}
