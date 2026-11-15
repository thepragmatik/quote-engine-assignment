package com.bendigo.quote.api;

import com.bendigo.quote.domain.CommissionCalculationStrategy;
import com.bendigo.quote.resilience.VendorQuotePort;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class QuoteController {

    private final VendorQuotePort vendorPort;
    private final CommissionCalculationStrategy strategy;

    public QuoteController(VendorQuotePort vendorPort, CommissionCalculationStrategy strategy) {
        this.vendorPort = vendorPort;
        this.strategy = strategy;
    }

    @PostMapping("/api/v1/quotes")
    public QuoteResponse createQuote(@Valid @RequestBody QuoteRequest request) {
        // FR-2 order: vendor verification happens FIRST; the quote is only computed for verified loans.
        vendorPort.verify(request.loanAmount(), request.loanTermInMonths(), request.riskBand());
        var quote = strategy.calculate(request.loanAmount(), request.loanTermInMonths(), request.riskBand());
        return new QuoteResponse(
                UUID.randomUUID().toString(), quote.commissionRate(), quote.totalCommission());
    }
}
