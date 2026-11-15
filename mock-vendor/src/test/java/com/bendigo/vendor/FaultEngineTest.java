package com.bendigo.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Pure unit tests verifying deterministic stochastic partitioning in {@link FaultEngine}.
 *
 * <p>Validates the contractual fault partitions at {@code faultRate = 0.20}:
 *
 * <ul>
 *   <li>[0.00, 0.10) -&gt; {@link FaultEngine.Outcome#ERROR_500} (10% probability)
 *   <li>[0.10, 0.15) -&gt; {@link FaultEngine.Outcome#BAD_REQUEST_400} (5% probability)
 *   <li>[0.15, 0.20) -&gt; {@link FaultEngine.Outcome#LATENCY_504} (5% probability)
 *   <li>[0.20, 1.00) -&gt; {@link FaultEngine.Outcome#OK} (80% probability)
 * </ul>
 */
@DisplayName("FaultEngine — Stochastic Partitioning Specifications")
class FaultEngineTest {

  private final FaultEngine engine = new FaultEngine(RandomGenerator.getDefault(), 0.2);

  @Nested
  @DisplayName("Default 20% Fault Rate Partitions")
  class DefaultRatePartitions {

    @Test
    @DisplayName("Draw in [0.00, 0.10) maps to HTTP 500 Internal Server Error")
    void classify_errorBand() {
      assertThat(engine.classify(0.0)).isEqualTo(FaultEngine.Outcome.ERROR_500);
      assertThat(engine.classify(0.05)).isEqualTo(FaultEngine.Outcome.ERROR_500);
      assertThat(engine.classify(0.0999)).isEqualTo(FaultEngine.Outcome.ERROR_500);
    }

    @Test
    @DisplayName("Draw in [0.10, 0.15) maps to HTTP 400 Bad Request")
    void classify_badRequestBand() {
      assertThat(engine.classify(0.10)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
      assertThat(engine.classify(0.125)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
      assertThat(engine.classify(0.1499)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
    }

    @Test
    @DisplayName("Draw in [0.15, 0.20) maps to HTTP 504 Gateway Timeout (Latency)")
    void classify_latencyBand() {
      assertThat(engine.classify(0.15)).isEqualTo(FaultEngine.Outcome.LATENCY_504);
      assertThat(engine.classify(0.175)).isEqualTo(FaultEngine.Outcome.LATENCY_504);
      assertThat(engine.classify(0.1999)).isEqualTo(FaultEngine.Outcome.LATENCY_504);
    }

    @Test
    @DisplayName("Draw in [0.20, 1.00) maps to HTTP 200 OK")
    void classify_okBand() {
      assertThat(engine.classify(0.20)).isEqualTo(FaultEngine.Outcome.OK);
      assertThat(engine.classify(0.50)).isEqualTo(FaultEngine.Outcome.OK);
      assertThat(engine.classify(0.9999)).isEqualTo(FaultEngine.Outcome.OK);
    }
  }

  @Nested
  @DisplayName("Boundary & Extreme Configuration Scenarios")
  class ExtremeRateScenarios {

    @Test
    @DisplayName("When FAULT_RATE is 0.0, all draws yield OK (deterministic happy path)")
    void zeroFaultRateAlwaysYieldsOk() {
      FaultEngine zeroFault = new FaultEngine(RandomGenerator.getDefault(), 0.0);
      assertThat(zeroFault.classify(0.0)).isEqualTo(FaultEngine.Outcome.OK);
      assertThat(zeroFault.classify(0.5)).isEqualTo(FaultEngine.Outcome.OK);
      assertThat(zeroFault.classify(0.99)).isEqualTo(FaultEngine.Outcome.OK);
    }

    @Test
    @DisplayName("When FAULT_RATE is 1.0, draws never yield OK")
    void fullFaultRateNeverYieldsOk() {
      FaultEngine fullFault = new FaultEngine(RandomGenerator.getDefault(), 1.0);
      assertThat(fullFault.classify(0.2)).isEqualTo(FaultEngine.Outcome.ERROR_500);
      assertThat(fullFault.classify(0.6)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
      assertThat(fullFault.classify(0.85)).isEqualTo(FaultEngine.Outcome.LATENCY_504);
    }
  }
}
