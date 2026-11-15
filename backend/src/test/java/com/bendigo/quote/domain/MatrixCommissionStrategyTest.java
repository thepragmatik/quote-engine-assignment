package com.bendigo.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("MatrixCommissionStrategy — Commission Calculation Specifications")
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

  @Nested
  @DisplayName("Happy Path & Canonical Pricing Scenarios")
  class HappyPathTests {

    @Test
    @DisplayName(
        "Canonical worked example: $250,000 / 36 months / MEDIUM produces 2.3100% and $5,775.00")
    void mediumThirtySixMonths() {
      // Rate = 0.0225 + 0.0002 * (36/12) = 0.0225 + 0.0006 = 0.0231 (4dp)
      // Total = 250,000.00 * 0.0231 = 5,775.00 (2dp)
      var q = strategy.calculate(new BigDecimal("250000.00"), 36, "MEDIUM");
      assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0231"));
      assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("5775.00"));
    }

    @Test
    @DisplayName("Banker's Rounding on Rate: 0.01505 ties round to even 0.0150 (LOW, 6 months)")
    void bankersRoundingOnRate() {
      // Rate = 0.0150 + 0.0001 * (6/12) = 0.0150 + 0.00005 = 0.01505
      // In HALF_EVEN, preceding digit '0' is even -> rounds DOWN to 0.0150 (HALF_UP would be
      // 0.0151).
      var q = strategy.calculate(new BigDecimal("1000.00"), 6, "LOW");
      assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0150"));
      assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName(
        "Rate tie rounds up when preceding digit is odd (HIGH, 6 months -> 0.03515 rounds to 0.0352)")
    void rateTieRoundsHalfEven() {
      // Rate = 0.0350 + 0.0003 * (6/12) = 0.0350 + 0.00015 = 0.03515
      // In HALF_EVEN, preceding digit '1' is odd -> rounds UP to 0.0352.
      var q = strategy.calculate(new BigDecimal("100.00"), 6, "HIGH");
      assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0352"));
      assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("3.52"));
    }

    @Test
    @DisplayName("Upper boundary: $10,000,000 / 360 months / HIGH executes accurately")
    void maximumPermissibleLoanBoundary() {
      // 360 / 12 = 30.0 years
      // Rate = 0.0350 + 0.0003 * 30 = 0.0350 + 0.0090 = 0.0440
      // Total = 10,000,000 * 0.0440 = 440,000.00
      var q = strategy.calculate(new BigDecimal("10000000.00"), 360, "HIGH");
      assertThat(q.commissionRate()).isEqualByComparingTo(new BigDecimal("0.0440"));
      assertThat(q.totalCommission()).isEqualByComparingTo(new BigDecimal("440000.00"));
    }

    @ParameterizedTest(name = "Non-terminating division with tenure {0} months evaluates cleanly")
    @ValueSource(ints = {7, 10, 11, 13, 23, 25, 35, 77})
    void nonTerminatingTenureDivisionsDoNotThrow(int months) {
      // Term / 12 produces an infinite repeating decimal for these months (e.g. 7/12 =
      // 0.5833333333...).
      // Verify that 10dp intermediate scale avoids ArithmeticException and produces valid quotes.
      var q = strategy.calculate(new BigDecimal("50000.00"), months, "MEDIUM");
      assertThat(q.commissionRate()).isNotNull();
      assertThat(q.totalCommission()).isNotNull();
      assertThat(q.commissionRate().scale()).isEqualTo(4);
      assertThat(q.totalCommission().scale()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("Sad Path & Defensive Input Validation")
  class SadPathTests {

    @Test
    @DisplayName("Throws InvalidLoanParameterException when loanAmount is null")
    void rejectsNullLoanAmount() {
      assertThatThrownBy(() -> strategy.calculate(null, 36, "MEDIUM"))
          .isInstanceOf(InvalidLoanParameterException.class)
          .hasMessageContaining("Loan amount must not be null");
    }

    @Test
    @DisplayName("Throws InvalidLoanParameterException when riskBand is null")
    void rejectsNullRiskBand() {
      assertThatThrownBy(() -> strategy.calculate(new BigDecimal("250000.00"), 36, null))
          .isInstanceOf(InvalidLoanParameterException.class)
          .hasMessageContaining("Please select a valid risk band");
    }

    @Test
    @DisplayName("Throws InvalidLoanParameterException when riskBand is blank")
    void rejectsBlankRiskBand() {
      assertThatThrownBy(() -> strategy.calculate(new BigDecimal("250000.00"), 36, "   "))
          .isInstanceOf(InvalidLoanParameterException.class)
          .hasMessageContaining("Please select a valid risk band");
    }

    @Test
    @DisplayName("Throws InvalidLoanParameterException when riskBand is not in the matrix")
    void rejectsUnknownRiskBand() {
      assertThatThrownBy(() -> strategy.calculate(new BigDecimal("250000.00"), 36, "NON_EXISTENT"))
          .isInstanceOf(InvalidLoanParameterException.class)
          .hasMessageContaining("Please select a valid risk band");
    }

    @Test
    @DisplayName("Strategy constructor rejects null matrix with IllegalArgumentException")
    void constructorRejectsNullMatrix() {
      assertThatThrownBy(() -> new MatrixCommissionStrategy(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Risk band matrix must not be null");
    }
  }
}
