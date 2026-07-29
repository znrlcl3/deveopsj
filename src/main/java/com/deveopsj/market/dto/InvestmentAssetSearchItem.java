package com.deveopsj.market.dto;

import com.deveopsj.assetplan.entity.InvestmentAsset;

public record InvestmentAssetSearchItem(
        Long id,
        String symbol,
        String assetName,
        String market,
        String assetClass,
        String currency) {

    public static InvestmentAssetSearchItem from(InvestmentAsset asset) {
        return new InvestmentAssetSearchItem(
                asset.getId(),
                asset.getSymbol(),
                asset.getAssetName(),
                asset.getMarket(),
                asset.getAssetClass(),
                asset.getCurrency());
    }
}
