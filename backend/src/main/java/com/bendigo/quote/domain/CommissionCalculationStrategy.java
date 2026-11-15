package com.bendigo.quote.domain;

import java.math.BigDecimal;

/** Strategy seam for commission calculation — swap implementations without touching the API layer. */
public interface CommissionCalculationStrategy {

    Quote calculate(BigDecimal loanAmount, int termMonths, String riskBand);

    record Quote(BigDecimal commissionRate, BigDecimal totalCommission) {}
}
