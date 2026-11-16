package com.deveopsj.market.dto;

import java.util.List;

public record PortfolioSummary(
        List<PortfolioPosition> positions,
        Long totalCostKrw,
        long quotedCostKrw,
        long totalValuationKrw,
        Long totalProfitLossKrw,
        List<com.deveopsj.assetplan.service.BalanceSnapshotService.View> snapshots) {
    public PortfolioSummary(List<PortfolioPosition> positions,Long cost,long quotedCost,long valuation,Long profit) {
        this(positions,cost,quotedCost,valuation,profit,List.of());
    }
    public boolean valuationComplete() { return positions.stream().allMatch(p->p.quoteAvailable() && p.valuationAmountKrw()!=null); }
    public java.math.BigInteger totalAssetsKrw() {
        if(!valuationComplete()) return null;
        var total=java.math.BigInteger.valueOf(totalValuationKrw);
        for(var s:snapshots) total=total.add(java.math.BigInteger.valueOf(s.data().total()).subtract(java.math.BigInteger.valueOf(s.data().totals().get("평가금액"))));
        return total;
    }
    public Long comparisonCostKrw() {
        if(positions.stream().anyMatch(p -> p.referenceCostKrw()==null)) return null;
        long total=positions.stream().mapToLong(PortfolioPosition::referenceCostKrw).reduce(0L,Math::addExact);
        for(var s:snapshots) total=Math.addExact(total,s.data().totals().get("매입금액"));
        return total;
    }
    public Long comparisonProfitKrw() {
        if(positions.stream().anyMatch(p -> p.fxProfitLossKrw()==null)) return null;
        long total=positions.stream().mapToLong(PortfolioPosition::fxProfitLossKrw).reduce(0L,Math::addExact);
        for(var s:snapshots) total=Math.addExact(total,s.data().totals().get("평가손익"));
        return total;
    }
    public boolean usesFileRate() { return positions.stream().anyMatch(PortfolioPosition::referenceCostEstimated); }
}
