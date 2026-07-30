package com.deveopsj.market.dto;

import java.math.BigDecimal;

public record MonthlyPortfolioPosition(
        Long assetPlanId,
        String planName,
        Long investmentAssetId,
        String symbol,
        String assetName,
        String market,
        String currency,
        BigDecimal quantity,
        BigDecimal averageUnitPrice,
        long remainingCostKrw) {
}
