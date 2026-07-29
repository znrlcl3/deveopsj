package com.deveopsj.market.dto;

public record InvestmentAssetSyncResult(int createdCount, int updatedCount) {

    public int totalCount() {
        return createdCount + updatedCount;
    }
}
