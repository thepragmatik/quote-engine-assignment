package com.bendigo.quote.resilience;

import java.math.BigDecimal;

/**
 * Swappable seam for third-party vendor verification. The resilience-decorated HTTP adapter is
 * the only implementation; a fake/stub port replaces it in tests.
 */
public interface VendorQuotePort {

    VendorVerification verify(BigDecimal loanAmount, int termMonths, String riskBand);

    record VendorVerification(String verificationCode, boolean verified) {}
}
