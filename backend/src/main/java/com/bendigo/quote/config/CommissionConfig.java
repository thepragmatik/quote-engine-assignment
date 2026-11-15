package com.bendigo.quote.config;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.domain.MatrixCommissionStrategy;
import com.bendigo.quote.domain.RiskBandMatrix;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing bean definitions and dependency injection wiring for the domain
 * calculation layer.
 */
@Configuration
public class CommissionConfig {

  /**
   * Design Note: Single wiring point for the commission calculation engine.
   *
   * <p>Decouples implementation choices from the web layer. Swap alternatives:
   *
   * <ol>
   *   <li>Database-backed or distributed rule matrix instead of application YAML.
   *   <li>Promotional or campaign-specific flat-rate fee calculation.
   *   <li>Multi-tenant or regional strategy routing based on caller context.
   * </ol>
   *
   * Substituting any variant requires changing only this bean definition.
   *
   * @param matrix externalized risk band specifications
   * @return active {@link CommissionCalculationStrategy} instance
   */
  @Bean
  CommissionCalculationStrategy commissionCalculationStrategy(RiskBandMatrix matrix) {
    return new MatrixCommissionStrategy(matrix.matrix());
  }
}
