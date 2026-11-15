package com.bendigo.quote.api;

import java.math.BigDecimal;

/** ATTENTION(money): BigDecimal is serialized directly — no float/double conversion anywhere. */
public record QuoteResponse(String quoteId, BigDecimal commissionRate, BigDecimal totalCommission) {}
