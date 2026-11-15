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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class QuoteControllerHappyPathTest {

    private static final String PATH = "/api/v1/vendor/quotes";
    private static final String VALID_REQUEST =
            "{\"loanAmount\":250000.00,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";

    static WireMockServer wm = new WireMockServer(WireMockConfiguration.options().port(8001));

    @Autowired MockMvc mockMvc;

    @DynamicPropertySource
    static void vendorProps(DynamicPropertyRegistry registry) {
        registry.add("vendor.api-url", () -> "http://localhost:8001");
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
                        // jsonPath coerces numerics to double, so assert the 2dp scale on the raw body
                        .andExpect(jsonPath("$.totalCommission").value(5775.0))
                        .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getContentAsString())
                .contains("\"totalCommission\":5775.00")
                .contains("\"commissionRate\":0.0231");
    }

    @Test
    void quoteIdParsesAsUuid() throws Exception {
        MvcResult result =
                mockMvc
                        .perform(
                                post("/api/v1/quotes")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(VALID_REQUEST))
                        .andExpect(status().isOk())
                        .andReturn();
        var id = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.quoteId");
        org.assertj.core.api.Assertions.assertThat(java.util.UUID.fromString((String) id).toString())
                .isEqualTo(id);
    }

    @Test
    void retriesOnceOnTransient500ThenSucceeds() throws Exception {
        wm.resetAll();
        stubFor(
                WireMock.post(urlEqualTo(PATH))
                        .inScenario("retry")
                        .whenScenarioStateIs(Scenario.STARTED)
                        .willReturn(aResponse().withStatus(500))
                        .willSetStateTo("recovered"));
        stubFor(
                WireMock.post(urlEqualTo(PATH))
                        .inScenario("retry")
                        .whenScenarioStateIs("recovered")
                        .willReturn(okJson("{\"verificationCode\":\"VC-abc\",\"verified\":true}")));

        mockMvc
                .perform(post("/api/v1/quotes").contentType(MediaType.APPLICATION_JSON).content(VALID_REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCommission").value(5775.0));

        verify(2, postRequestedFor(urlEqualTo(PATH)));
        verify(
                postRequestedFor(urlEqualTo(PATH))
                        .withHeader("x-api-key", equalTo("dev-vendor-secret")));
    }
}
