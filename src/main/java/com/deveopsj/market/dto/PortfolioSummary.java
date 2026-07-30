package com.deveopsj.market.dto;

import java.util.List;

public record PortfolioSummary(
        List<PortfolioPosition> positions,
        long totalCostKrw,
        long quotedCostKrw,
        long totalValuationKrw,
        long totalProfitLossKrw) {
}
