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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class QuoteControllerResilienceTest {

    private static final String PATH = "/api/v1/vendor/quotes";
    private static final String VALID_REQUEST =
            "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";
    private static final String VENDOR_OK_BODY = "{\"verificationCode\":\"VC-abc\",\"verified\":true}";

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
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andReturn();
    }

    private int statusOf(MvcResult result) {
        return result.getResponse().getStatus();
    }

    @Test
    void threeConsecutiveVendor500sExhaustRetryAndReturn502() throws Exception {
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
        verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void vendor401IsNotRetried() throws Exception {
        wm.resetAll();
        stubVendor(401);
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
        verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void vendor400IsNotRetried() throws Exception {
        wm.resetAll();
        stubVendor(400);
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
        verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void circuitOpensAfterFiveConsecutiveFailingCalls() throws Exception {
        // CircuitBreaker sits OUTSIDE Retry, so it records ONE logical call per request:
        // calls 1-5 exhaust retries (3 HTTP attempts each -> 502); the 5th recorded failure
        // trips the circuit (minimum-number-of-calls=5, 100% failure >= 50% threshold) and
        // every later call short-circuits to 503 with NO vendor traffic at all.
        var statuses = new java.util.ArrayList<Integer>();
        for (int i = 0; i < 5; i++) {
            statuses.add(statusOf(postQuote()));
            assertThat(statuses.get(i)).isEqualTo(502);
        }

        assertThat(statusOf(postQuote())).isEqualTo(503);
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
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
    void circuitRecoversThroughHalfOpenProbe() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(statusOf(postQuote())).isEqualTo(502);
        }
        assertThat(statusOf(postQuote())).isEqualTo(503);
        // circuit is OPEN now; let the open-state window elapse (100ms in test profile)
        Thread.sleep(300);
        wm.resetAll();
        stubVendorOk();

        // HALF_OPEN probe succeeds -> circuit transitions to CLOSED
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk());

        // subsequent call also succeeds without a probe -> circuit is CLOSED again
        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    void vendorLatencyBeyondTimeLimiterYields502() throws Exception {
        wm.resetAll();
        stubFor(WireMock.post(urlEqualTo(PATH)).willReturn(aResponse().withFixedDelay(2100).withStatus(200)));

        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Vendor verification unavailable. Please retry."));
    }
}
