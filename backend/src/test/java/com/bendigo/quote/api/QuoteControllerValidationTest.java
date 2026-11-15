package com.bendigo.quote.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.resilience.VendorQuotePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
class QuoteControllerValidationTest {

    @Autowired MockMvc mockMvc;

    @MockitoBean VendorQuotePort vendorQuotePort;

    @MockitoBean CommissionCalculationStrategy commissionCalculationStrategy;

    private static final MediaType JSON = MediaType.APPLICATION_JSON;

    @Test
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
    void missingFieldIsRejected() throws Exception {
        mockMvc
                .perform(
                        post("/api/v1/quotes")
                                .contentType(JSON)
                                .content("{\"loanAmount\":250000.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void malformedJsonIsRejected() throws Exception {
        mockMvc
                .perform(post("/api/v1/quotes").contentType(JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }
}
