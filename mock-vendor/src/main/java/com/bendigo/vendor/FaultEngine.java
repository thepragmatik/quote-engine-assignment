package com.bendigo.vendor;

import java.util.random.RandomGenerator;

/**
 * Stochastic fault engine for the mock third-party vendor quote service.
 *
 * <p>Simulates real-world network turbulence, downstream service degradation, and latency spikes to
 * exercise client resilience architectures without needing actual third-party partner downtime.
 *
 * <p><b>Fault Rate Distribution Model:</b> The {@code faultRate} represents a fraction within
 * {@code [0.0, 1.0]}. When a uniformly distributed random draw {@code r in [0.0, 1.0)} is
 * generated:
 *
 * <ul>
 *   <li>{@code r >= faultRate}: {@link Outcome#OK} (Normal operation, returns HTTP 200)
 *   <li>{@code r < faultRate / 2}: {@link Outcome#ERROR_500} (Transient partner failure, returns
 *       HTTP 500)
 *   <li>{@code faultRate / 2 <= r < faultRate * 0.75}: {@link Outcome#BAD_REQUEST_400} (Partner
 *       client error, returns HTTP 400)
 *   <li>{@code r >= faultRate * 0.75}: {@link Outcome#LATENCY_504} (Timeout simulation, sleeps and
 *       returns HTTP 504)
 * </ul>
 *
 * <p>At default {@code FAULT_RATE = 0.20}, the outcome split is: <b>80% OK / 10% HTTP 500 / 5% HTTP
 * 400 / 5% HTTP 504 (latency)</b>.
 *
 * <p>ATTENTION(resilience): The classifier method {@link #classify(double)} is a pure,
 * deterministic mathematical function, completely isolated from random draw generation to enable
 * comprehensive unit test verification.
 *
 * @param random the random number generator used for stochastic draws
 * @param faultRate the configured fractional probability of injecting a fault (0.0 to 1.0)
 */
public record FaultEngine(RandomGenerator random, double faultRate) {

  /**
   * Maps a normalized random draw value into a deterministic fault outcome.
   *
   * @param r a normalized float value in [0.0, 1.0)
   * @return the classified {@link Outcome}
   */
  public Outcome classify(double r) {
    if (r >= faultRate) {
      return Outcome.OK;
    }
    // ATTENTION(resilience): round partition thresholds to 4 decimal places to prevent IEEE 754
    // binary floating-point epsilon dust (e.g. 0.2 * 0.75 evaluating to 0.15000000000000002).
    double errorThreshold = Math.round(faultRate * 0.5 * 10000.0) / 10000.0;
    if (r < errorThreshold) {
      return Outcome.ERROR_500;
    }
    double badRequestThreshold = Math.round(faultRate * 0.75 * 10000.0) / 10000.0;
    if (r < badRequestThreshold) {
      return Outcome.BAD_REQUEST_400;
    }
    return Outcome.LATENCY_504;
  }

  /** Enumeration of simulated HTTP outcomes produced by the mock vendor partner. */
  public enum Outcome {
    /** Successful partner verification with metadata payload (HTTP 200). */
    OK,
    /** Internal server failure simulating upstream crash or database fault (HTTP 500). */
    ERROR_500,
    /** Client-side validation failure or bad partner payload (HTTP 400). */
    BAD_REQUEST_400,
    /** Downstream latency threshold breach resulting in gateway timeout (HTTP 504). */
    LATENCY_504
  }
}
