package com.bendigo.vendor;

import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Dependency injection configuration for the mock vendor service. */
@Configuration
public class VendorConfig {

  /**
   * Resilience Note: Bean definition providing the active {@link FaultEngine}.
   *
   * <p>Serves as the primary swap seam: integration test suites replace this with a mock or
   * fixed-seed generator to assert fault responses deterministically, and alternative chaos
   * engineering profiles can be plugged in seamlessly.
   *
   * @param faultRate fractional fault probability injected via {@code vendor.fault-rate} (defaults
   *     to 0.20)
   * @return configured {@link FaultEngine} instance
   */
  @Bean
  public FaultEngine faultEngine(@Value("${vendor.fault-rate:0.2}") double faultRate) {
    return new FaultEngine(RandomGenerator.getDefault(), faultRate);
  }
}
