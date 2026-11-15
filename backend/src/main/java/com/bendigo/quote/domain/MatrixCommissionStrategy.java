package com.bendigo.quote.domain;

import com.bendigo.quote.api.QuoteRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Strategy implementation executing commission rate calculations derived from an externalized
 * {@link RiskBandMatrix}.
 *
 * <p><b>Calculation Formula:</b>
 *
 * <pre>
 *   loanTenureInYears = loanTermInMonths / 12  (computed to 10 decimal places)
 *   rawCommissionRate = baseRate + (termMultiplier * loanTenureInYears)
 *   commissionRate    = roundRate4(rawCommissionRate) [HALF_EVEN to 4 decimal places]
 *   totalCommission   = roundMoney2(loanAmount * commissionRate) [HALF_EVEN to 2 decimal places]
 * </pre>
 *
 * <p><b>Arbitrary-Precision Division:</b> Standard {@link BigDecimal#divide(BigDecimal)} throws
 * {@link ArithmeticException} when the quotient has a non-terminating decimal expansion (for
 * example, {@code 8 / 12 = 0.6666...}). Tenure in years is therefore evaluated with an explicit
 * intermediate precision of 10 decimal places before final rate quantization.
 */
public final class MatrixCommissionStrategy implements CommissionCalculationStrategy {

  /** Divisor representing the number of months in a calendar year. */
  private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

  /**
   * Intermediate scale for arbitrary precision tenure division to prevent non-terminating expansion
   * errors.
   */
  private static final int YEARS_DIVISION_SCALE = 10;

  private final Map<String, RiskBandMatrix.RateSpec> matrix;

  /**
   * Constructs a strategy bound to the given risk band specifications.
   *
   * @param matrix map of risk band identifier to its base rate and multiplier
   */
  public MatrixCommissionStrategy(Map<String, RiskBandMatrix.RateSpec> matrix) {
    if (matrix == null) {
      throw new IllegalArgumentException("Risk band matrix must not be null");
    }
    this.matrix = Map.copyOf(matrix);
  }

  @Override
  public Quote calculate(BigDecimal loanAmount, int termMonths, String riskBand) {
    if (loanAmount == null) {
      throw new InvalidLoanParameterException(QuoteRequest.LOAN_AMOUNT_REQUIRED_MSG);
    }
    if (riskBand == null || riskBand.isBlank()) {
      throw new InvalidLoanParameterException(QuoteRequest.RISK_BAND_MSG);
    }
    var spec = matrix.get(riskBand);
    if (spec == null) {
      throw new InvalidLoanParameterException(QuoteRequest.RISK_BAND_MSG);
    }

    // ATTENTION(money): plain divide() throws ArithmeticException for non-terminating quotients
    // (e.g. 8/12 = 2/3); use an explicit intermediate scale of 10 decimal places.
    var years =
        BigDecimal.valueOf(termMonths)
            .divide(MONTHS_PER_YEAR, YEARS_DIVISION_SCALE, RoundingMode.HALF_EVEN);

    // ATTENTION(money): rate is quantized to 4dp BEFORE multiplying by the amount (frozen rule).
    var unscaledRate = spec.baseRate().add(spec.termMultiplier().multiply(years));
    var rate = MoneyPolicy.roundRate4(unscaledRate);

    // Monetary total rounded with Banker's Rounding (HALF_EVEN) to standard currency scale (2dp).
    var total = MoneyPolicy.roundMoney2(loanAmount.multiply(rate));

    return new Quote(rate, total);
  }
}
