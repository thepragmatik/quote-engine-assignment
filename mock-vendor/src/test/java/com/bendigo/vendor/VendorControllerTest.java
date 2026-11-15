package com.bendigo.vendor;

import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class VendorControllerTest {

    /** Makes the LATENCY_504 sleep exactly 0ms so the 504 test is fast and deterministic. */
    @org.springframework.test.context.DynamicPropertySource
    static void latencyZero(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("vendor.latency-min-ms", () -> "0");
        registry.add("vendor.latency-max-ms", () -> "0");
    }


    private static final String BODY =
            "{\"loanAmount\":250000,\"loanTermInMonths\":36,\"riskBand\":\"MEDIUM\"}";

    @Autowired MockMvc mockMvc;

    @MockitoBean FaultEngine faultEngine;

    @BeforeEach
    void setUp() {
        // deterministic draw for the controller (used only by the LATENCY path in practice)
        when(faultEngine.random()).thenReturn(RandomGenerator.getDefault());
    }

    private void forced(FaultEngine.Outcome outcome) {
        when(faultEngine.classify(anyDouble())).thenReturn(outcome);
    }

    @Test
    void missingApiKey_unauthorized() throws Exception {
        forced(FaultEngine.Outcome.OK);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .contentType("application/json"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Unauthorized"));
    }

    @Test
    void wrongApiKey_unauthorized() throws Exception {
        forced(FaultEngine.Outcome.OK);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .header("x-api-key", "nope").contentType("application/json"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.detail").value("Unauthorized"));
    }

    @Test
    void forcedOk_returnsVerificationCode() throws Exception {
        forced(FaultEngine.Outcome.OK);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .header("x-api-key", "dev-vendor-secret").contentType("application/json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.verificationCode").value(org.hamcrest.Matchers.startsWith("VC-")))
            .andExpect(jsonPath("$.verified").value(true));
    }

    @Test
    void forcedError_returns500() throws Exception {
        forced(FaultEngine.Outcome.ERROR_500);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .header("x-api-key", "dev-vendor-secret").contentType("application/json"))
            .andExpect(status().isInternalServerError());
    }

    @Test
    void forcedBadRequest_returns400() throws Exception {
        forced(FaultEngine.Outcome.BAD_REQUEST_400);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .header("x-api-key", "dev-vendor-secret").contentType("application/json"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void forcedLatency_returns504() throws Exception {
        forced(FaultEngine.Outcome.LATENCY_504);
        mockMvc.perform(post("/api/v1/vendor/quotes").content(BODY)
                .header("x-api-key", "dev-vendor-secret").contentType("application/json"))
            .andExpect(status().isGatewayTimeout());
    }
}
