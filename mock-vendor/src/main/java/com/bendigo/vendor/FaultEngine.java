package com.bendigo.vendor;

import java.util.random.RandomGenerator;

/**
 * Stochastic fault engine for the mock vendor.
 *
 * <p>FAULT RATE CONTRACT: {@code faultRate} is a FRACTION in [0,1]. At 0.2 the split is
 * 80% ok / 10% 500 / 5% 400 / 5% latency-504.
 *
 * <p>ATTENTION(resilience): this class is the deliberate swap seam between the
 * deterministic classifier ({@link #classify(double)}, pure and unit-testable) and the
 * random draw ({@code random.nextDouble()}), so tests can pin either side.
 */
public record FaultEngine(RandomGenerator random, double faultRate) {

    public Outcome classify(double r) {
        if (r >= faultRate) {
            return Outcome.OK;
        }
        if (r < faultRate / 2) {
            return Outcome.ERROR_500;
        }
        if (r < faultRate * 3 / 4) {
            return Outcome.BAD_REQUEST_400;
        }
        return Outcome.LATENCY_504;
    }

    public enum Outcome {
        OK,
        ERROR_500,
        BAD_REQUEST_400,
        LATENCY_504
    }
}
