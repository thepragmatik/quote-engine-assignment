package com.bendigo.quote.api;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.resilience.VendorQuotePort;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller orchestrating loan quotation requests.
 *
 * <p>Implements the end-to-end workflow outlined in Functional Requirement FR-2:
 *
 * <ol>
 *   <li>Validates the incoming {@link QuoteRequest} payload against boundary constraints.
 *   <li>Communicates with the external partner vendor API via {@link VendorQuotePort} to obtain
 *       partner verification.
 *   <li>Executes financial commission calculation via {@link CommissionCalculationStrategy} using
 *       Banker's Rounding.
 *   <li>Returns a structured {@link QuoteResponse} containing the unique quote ID and computed
 *       monetary values.
 * </ol>
 */
@RestController
public class QuoteController {

  private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

  private final VendorQuotePort vendorPort;
  private final CommissionCalculationStrategy strategy;

  public QuoteController(VendorQuotePort vendorPort, CommissionCalculationStrategy strategy) {
    this.vendorPort = vendorPort;
    this.strategy = strategy;
  }

  /**
   * Calculates and issues a commission quote.
   *
   * @param request validated client quote parameters
   * @return generated quote details with UUID and calculated payout amounts
   */
  @PostMapping("/api/v1/quotes")
  public QuoteResponse createQuote(@Valid @RequestBody QuoteRequest request) {
    log.info(
        "Processing commission quote request: amount={}, termMonths={}, riskBand={}",
        request.loanAmount(),
        request.loanTermInMonths(),
        request.riskBand());

    // FR-2 orchestration: vendor verification occurs FIRST; commission is computed only for
    // verified partners.
    vendorPort.verify(request.loanAmount(), request.loanTermInMonths(), request.riskBand());

    var quote =
        strategy.calculate(request.loanAmount(), request.loanTermInMonths(), request.riskBand());

    String quoteId = UUID.randomUUID().toString();
    log.info(
        "Successfully issued quote: id={}, rate={}, total={}",
        quoteId,
        quote.commissionRate(),
        quote.totalCommission());

    return new QuoteResponse(quoteId, quote.commissionRate(), quote.totalCommission());
  }
}
