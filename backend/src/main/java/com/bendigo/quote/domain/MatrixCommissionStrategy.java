package com.bendigo.quote.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/** Strategy-pattern implementation driven by the externalized {@link RiskBandMatrix}. */
public class MatrixCommissionStrategy implements CommissionCalculationStrategy {

    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);
    private static final int YEARS_SCALE = 10;

    private final Map<String, RiskBandMatrix.RateSpec> matrix;

    public MatrixCommissionStrategy(Map<String, RiskBandMatrix.RateSpec> matrix) {
        this.matrix = Map.copyOf(matrix);
    }

    @Override
    public Quote calculate(BigDecimal loanAmount, int termMonths, String riskBand) {
        var spec = matrix.get(riskBand);
        if (spec == null) {
            throw new InvalidLoanParameterException("Please select a valid risk band.");
        }
        // ATTENTION(money): plain divide() throws ArithmeticException for non-terminating quotients
        // (e.g. 8/12 = 2/3); use a 10dp intermediate scale, then quantize.
        var years = BigDecimal.valueOf(termMonths).divide(TWELVE, YEARS_SCALE, RoundingMode.HALF_EVEN);
        // ATTENTION(money): rate is quantized to 4dp BEFORE multiplying by the amount (frozen rule).
        var rate = MoneyPolicy.roundRate4(spec.baseRate().add(spec.termMultiplier().multiply(years)));
        var total = MoneyPolicy.roundMoney2(loanAmount.multiply(rate));
        return new Quote(rate, total);
    }
}
