package com.deveopsj.market.dto;

import java.util.List;

public record PortfolioSummary(
        List<PortfolioPosition> positions,
        Long totalCostKrw,
        long quotedCostKrw,
        long totalValuationKrw,
        Long totalProfitLossKrw) {
    public Long comparisonCostKrw() {
        if(positions.stream().anyMatch(p -> p.referenceCostKrw()==null)) return null;
        return positions.stream().mapToLong(PortfolioPosition::referenceCostKrw).reduce(0L,Math::addExact);
    }
    public Long comparisonProfitKrw() {
        if(positions.stream().anyMatch(p -> p.fxProfitLossKrw()==null)) return null;
        return positions.stream().mapToLong(PortfolioPosition::fxProfitLossKrw).reduce(0L,Math::addExact);
    }
    public boolean usesFileRate() { return positions.stream().anyMatch(PortfolioPosition::referenceCostEstimated); }
}
