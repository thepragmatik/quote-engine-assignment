package com.bendigo.quote.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Single rounding seam for all money math. Never round inline anywhere else. */
public final class MoneyPolicy {

    private MoneyPolicy() {}

    // ATTENTION(money): HALF_EVEN (banker's rounding) is the frozen contract for rate quantization;
    // changing the mode or scale silently re-prices every quote in the system.
    public static BigDecimal roundRate4(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_EVEN);
    }

    // ATTENTION(money): HALF_EVEN at 2dp for all monetary amounts; BigDecimal only, never double/float.
    public static BigDecimal roundMoney2(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_EVEN);
    }
}
