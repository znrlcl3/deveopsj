package com.deveopsj.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MarketPriceQuote(
        Long investmentAssetId,
        String symbol,
        String assetName,
        String market,
        String currency,
        BigDecimal currentPrice,
        BigDecimal previousClose,
        BigDecimal change,
        BigDecimal changeRate,
        LocalDateTime fetchedAt,
        boolean cached,
        boolean stale) {

    public MarketPriceQuote asCached(boolean stale) {
        return new MarketPriceQuote(
                investmentAssetId, symbol, assetName, market, currency,
                currentPrice, previousClose, change, changeRate, fetchedAt, true, stale);
    }
}
