package com.bendigo.quote.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests validating Banker's Rounding (HALF_EVEN) rules and null safety in {@link MoneyPolicy}.
 */
@DisplayName("MoneyPolicy — Banker's Rounding Precision Specifications")
class MoneyPolicyTest {

  @Nested
  @DisplayName("roundRate4 — 4-decimal place rate quantization")
  class RoundRate4Tests {

    @Test
    @DisplayName("HALF_EVEN tie rounds towards the even neighbor (odd preceding digit rounds up)")
    void tieRoundsUpWhenPrecedingDigitIsOdd() {
      // 0.01255: target 4dp, 5th digit is 5 with no remainder.
      // 4th digit is '5' (odd) -> rounds UP to even '6'.
      BigDecimal input = new BigDecimal("0.01255");
      assertThat(MoneyPolicy.roundRate4(input)).isEqualByComparingTo(new BigDecimal("0.0126"));
    }

    @Test
    @DisplayName(
        "HALF_EVEN tie rounds towards the even neighbor (even preceding digit rounds down)")
    void tieRoundsDownWhenPrecedingDigitIsEven() {
      // 0.01265: target 4dp, 5th digit is 5 with no remainder.
      // 4th digit is '6' (even) -> rounds DOWN to even '6'.
      BigDecimal input = new BigDecimal("0.01265");
      assertThat(MoneyPolicy.roundRate4(input)).isEqualByComparingTo(new BigDecimal("0.0126"));
    }

    @Test
    @DisplayName("Non-tie values follow standard nearest-neighbor rounding")
    void nonTieNearestNeighbor() {
      assertThat(MoneyPolicy.roundRate4(new BigDecimal("0.01234")))
          .isEqualByComparingTo(new BigDecimal("0.0123"));
      assertThat(MoneyPolicy.roundRate4(new BigDecimal("0.01236")))
          .isEqualByComparingTo(new BigDecimal("0.0124"));
    }

    @Test
    @DisplayName("Rejects null input with IllegalArgumentException")
    void rejectsNullRate() {
      assertThatThrownBy(() -> MoneyPolicy.roundRate4(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }
  }

  @Nested
  @DisplayName("roundMoney2 — 2-decimal place currency rounding")
  class RoundMoney2Tests {

    @Test
    @DisplayName("HALF_EVEN tie rounds towards the even cent (odd preceding cent rounds up)")
    void tieRoundsUpWhenPrecedingCentIsOdd() {
      // 2.355: target 2dp (cents). 3rd digit is 5.
      // 2nd digit is '5' (odd) -> rounds UP to '6' (even).
      BigDecimal input = new BigDecimal("2.355");
      assertThat(MoneyPolicy.roundMoney2(input)).isEqualByComparingTo(new BigDecimal("2.36"));
    }

    @Test
    @DisplayName("HALF_EVEN tie rounds towards the even cent (even preceding cent rounds down)")
    void tieRoundsDownWhenPrecedingCentIsEven() {
      // 2.345: target 2dp (cents). 3rd digit is 5.
      // 2nd digit is '4' (even) -> rounds DOWN to '4' (even).
      BigDecimal input = new BigDecimal("2.345");
      assertThat(MoneyPolicy.roundMoney2(input)).isEqualByComparingTo(new BigDecimal("2.34"));
    }

    @Test
    @DisplayName("Non-tie values follow standard nearest-neighbor rounding")
    void nonTieNearestNeighbor() {
      assertThat(MoneyPolicy.roundMoney2(new BigDecimal("10.504")))
          .isEqualByComparingTo(new BigDecimal("10.50"));
      assertThat(MoneyPolicy.roundMoney2(new BigDecimal("10.506")))
          .isEqualByComparingTo(new BigDecimal("10.51"));
    }

    @Test
    @DisplayName("Preserves trailing zeros to two decimal places")
    void preservesTwoDecimalPlaces() {
      BigDecimal result = MoneyPolicy.roundMoney2(new BigDecimal("100"));
      assertThat(result.scale()).isEqualTo(2);
      assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Rejects null input with IllegalArgumentException")
    void rejectsNullMoney() {
      assertThatThrownBy(() -> MoneyPolicy.roundMoney2(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("must not be null");
    }
  }
}
