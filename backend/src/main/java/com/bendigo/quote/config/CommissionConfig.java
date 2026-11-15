package com.bendigo.quote.config;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.domain.MatrixCommissionStrategy;
import com.bendigo.quote.domain.RiskBandMatrix;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CommissionConfig {

    // ATTENTION(design): single wiring point for the calculator. Swap variants:
    // (1) matrix sourced from a database instead of application.yml, (2) segmented/tiered
    // strategies, (3) strategies selected by feature flag or tenant — only this @Bean method changes.
    @Bean
    CommissionCalculationStrategy commissionCalculationStrategy(RiskBandMatrix matrix) {
        return new MatrixCommissionStrategy(matrix.matrix());
    }
}
