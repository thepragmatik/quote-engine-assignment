package com.bendigo.quote.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Integration test suite asserting the end-to-end happy path and transient retry recovery. */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("QuoteControllerHappyPathTest — End-to-End Orchestration & Transient Recovery")
class QuoteControllerHappyPathTest {

  private static final String PATH = "/api/v1/vendor/quotes";
  private static final String VALID_REQUEST =
      "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";

  static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().dynamicPort());

  @Autowired MockMvc mockMvc;

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
  void stubVendorOk() {
    wm.resetAll();
    stubFor(
        WireMock.post(urlEqualTo(PATH))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody("{\"verificationCode\":\"VC-abc\",\"verified\":true}")));
  }

  @Test
  @DisplayName(
      "Successfully calculates quote matching canonical example ($250k / 36 mo / MEDIUM -> 0.0231 / $5,775.00)")
  void returnsQuoteWithCanonicalExampleValues() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.quoteId", matchesPattern("^[0-9a-fA-F-]{36}$")))
            .andExpect(jsonPath("$.commissionRate").value("0.0231"))
            // jsonPath coerces numeric decimals to double; assert exact 2dp currency scale on raw
            // JSON body
            .andExpect(jsonPath("$.totalCommission").value(5775.0))
            .andReturn();

    org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
        .contains("\"totalCommission\":5775.00")
        .contains("\"commissionRate\":0.0231");
  }

  @Test
  @DisplayName("Generates a valid UUID v4 quote identifier on every successful quote")
  void quoteIdParsesAsValidUuid() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/quotes")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_REQUEST))
            .andExpect(status().isOk())
            .andReturn();

    String id =
        com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.quoteId");
    UUID parsedUuid = UUID.fromString(id);
    org.assertj.core.api.Assertions.assertThat(parsedUuid).isNotNull();
    org.assertj.core.api.Assertions.assertThat(parsedUuid.version()).isEqualTo(4);
  }

  @Test
  @DisplayName("Recovers from transient HTTP 500 on first vendor attempt and succeeds on retry")
  void retriesOnceOnTransient500ThenSucceeds() throws Exception {
    wm.resetAll();
    // WireMock stateful scenario: first call returns 500 and transitions state to 'recovered'
    stubFor(
        WireMock.post(urlEqualTo(PATH))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withStatus(500))
            .willSetStateTo("recovered"));
    // Second call in state 'recovered' returns HTTP 200 with verification metadata
    stubFor(
        WireMock.post(urlEqualTo(PATH))
            .inScenario("retry")
            .whenScenarioStateIs("recovered")
            .willReturn(okJson("{\"verificationCode\":\"VC-abc\",\"verified\":true}")));

    mockMvc
        .perform(
            post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalCommission").value(5775.0));

    // Verify that exactly 2 HTTP attempts were made and x-api-key was injected
    verify(2, postRequestedFor(urlEqualTo(PATH)));
    verify(
        postRequestedFor(urlEqualTo(PATH)).withHeader("x-api-key", equalTo("dev-vendor-secret")));
  }
}
