package com.deveopsj.market.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PortfolioPosition(
        Long assetPlanId,
        String planName,
        Long investmentAssetId,
        String symbol,
        String assetName,
        String market,
        String currency,
        BigDecimal quantity,
        BigDecimal averageUnitPrice,
        long remainingCostKrw,
        BigDecimal currentPrice,
        BigDecimal appliedExchangeRate,
        Long valuationAmountKrw,
        Long profitLossKrw,
        BigDecimal returnRate,
        LocalDateTime quoteFetchedAt,
        boolean cachedQuote,
        boolean staleQuote,
        String quoteError) {

    public boolean quoteAvailable() {
        return quoteError == null;
    }
}
