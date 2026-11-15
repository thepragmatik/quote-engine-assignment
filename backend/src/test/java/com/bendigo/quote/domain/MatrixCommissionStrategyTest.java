package com.bendigo.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MatrixCommissionStrategyTest {

    private final MatrixCommissionStrategy strategy =
            new MatrixCommissionStrategy(
                    Map.of(
                            "LOW",
                            new RiskBandMatrix.RateSpec(new BigDecimal("0.0150"), new BigDecimal("0.0001")),
                            "MEDIUM",
                            new RiskBandMatrix.RateSpec(new BigDecimal("0.0225"), new BigDecimal("0.0002")),
                            "HIGH",
                            new RiskBandMatrix.RateSpec(new BigDecimal("0.0350"), new BigDecimal("0.0003"))));

    @Test
    void mediumThirtySixMonths() {
        var q = strategy.calculate(new BigDecimal("250000.00"), 36, "MEDIUM");
        assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0231"));
        assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("5775.00"));
    }

    @Test
    void bankersRoundingOnRate() {
        var q = strategy.calculate(new BigDecimal("1000.00"), 6, "LOW");
        assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0150"));
        assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    void rateTieRoundsHalfEven() {
        var q = strategy.calculate(new BigDecimal("100.00"), 6, "HIGH");
        assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0352"));
        assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("3.52"));
    }
}
