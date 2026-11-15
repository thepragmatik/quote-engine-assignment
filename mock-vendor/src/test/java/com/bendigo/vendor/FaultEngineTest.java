package com.bendigo.vendor;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

/**
 * Pure-deterministic tests of the fault classifier — no Spring context.
 *
 * Bands at faultRate=0.2 (contract: 80% ok / 10% 500 / 5% 400 / 5% latency-504):
 * [0, 0.1) ERROR_500, [0.1, 0.15) BAD_REQUEST_400, [0.15, 0.2) LATENCY_504, [0.2, 1) OK.
 */
class FaultEngineTest {

    private final FaultEngine engine = new FaultEngine(RandomGenerator.getDefault(), 0.2);

    @Test
    void classify_errorBand() {
        assertThat(engine.classify(0.09)).isEqualTo(FaultEngine.Outcome.ERROR_500);
    }

    @Test
    void classify_badRequestBand() {
        assertThat(engine.classify(0.15)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
        assertThat(engine.classify(0.11)).isEqualTo(FaultEngine.Outcome.BAD_REQUEST_400);
    }

    @Test
    void classify_latencyBand() {
        assertThat(engine.classify(0.19)).isEqualTo(FaultEngine.Outcome.LATENCY_504);
    }

    @Test
    void classify_okBand() {
        assertThat(engine.classify(0.2)).isEqualTo(FaultEngine.Outcome.OK);
    }
}
