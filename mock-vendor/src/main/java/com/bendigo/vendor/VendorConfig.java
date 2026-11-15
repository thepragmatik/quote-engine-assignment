package com.bendigo.vendor;

import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the FaultEngine bean from a @Configuration point.
 *
 * <p>ATTENTION(resilience): this bean definition is the swap seam — tests replace it
 * with a {@code @MockitoBean} stub, and a different vendor could swap in another engine.
 */
@Configuration
public class VendorConfig {

    @Bean
    public FaultEngine faultEngine(@Value("${vendor.fault-rate:0.2}") double faultRate) {
        return new FaultEngine(RandomGenerator.getDefault(), faultRate);
    }
}
