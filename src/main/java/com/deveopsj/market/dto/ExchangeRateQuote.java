package com.deveopsj.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExchangeRateQuote(
        String currency,
        BigDecimal krwRate,
        LocalDateTime fetchedAt,
        boolean cached,
        boolean stale) {

    public ExchangeRateQuote asCached(boolean stale) {
        return new ExchangeRateQuote(currency, krwRate, fetchedAt, true, stale);
    }
}
