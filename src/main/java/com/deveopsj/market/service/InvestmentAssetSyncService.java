package com.deveopsj.market.service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.market.dto.InvestmentAssetSyncResult;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.dto.KrxStockItem;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InvestmentAssetSyncService {

    private final InvestmentAssetRepository investmentAssetRepository;

    @Transactional
    public InvestmentAssetSyncResult synchronize(List<KrxStockItem> stocks, KrxMarket sourceMarket) {
        Map<String, InvestmentAsset> existingAssets = new HashMap<>();
        investmentAssetRepository.findAll().forEach(asset ->
                existingAssets.put(key(asset.getMarket(), asset.getSymbol()), asset));

        Map<String, InvestmentAsset> changedAssets = new LinkedHashMap<>();
        int createdCount = 0;
        int updatedCount = 0;
        for (KrxStockItem stock : stocks) {
            String symbol = normalize(stock.shortCode());
            if (symbol.isBlank()) {
                continue;
            }
            String market = resolveMarket(stock.market(), sourceMarket);
            String assetKey = key(market, symbol);
            InvestmentAsset asset = existingAssets.get(assetKey);
            if (asset == null) {
                asset = new InvestmentAsset();
                asset.setMarket(market);
                asset.setSymbol(symbol);
                existingAssets.put(assetKey, asset);
                createdCount++;
            } else {
                updatedCount++;
            }
            asset.setAssetName(resolveName(stock));
            asset.setAssetClass(resolveAssetClass(stock, sourceMarket));
            asset.setCurrency(sourceMarket == KrxMarket.US ? "USD" : "KRW");
            asset.setActive(true);
            changedAssets.put(assetKey, asset);
        }
        investmentAssetRepository.saveAll(changedAssets.values());
        return new InvestmentAssetSyncResult(createdCount, updatedCount);
    }

    private String resolveName(KrxStockItem stock) {
        String name = stock.abbreviatedName();
        if (name == null || name.isBlank()) {
            name = stock.name();
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("종목명이 없는 데이터는 동기화할 수 없습니다.");
        }
        String normalizedName = name.trim();
        return normalizedName.length() > 100 ? normalizedName.substring(0, 100) : normalizedName;
    }

    private String resolveMarket(String market, KrxMarket sourceMarket) {
        if (market != null && !market.isBlank()) {
            return normalize(market).replace(' ', '_');
        }
        return sourceMarket == KrxMarket.ALL ? "KOSPI" : sourceMarket.name();
    }

    private String resolveAssetClass(KrxStockItem stock, KrxMarket sourceMarket) {
        if (sourceMarket == KrxMarket.ETF
                || "ETF".equalsIgnoreCase(stock.securityGroup())
                || "ETF".equalsIgnoreCase(stock.stockType())) {
            return "ETF";
        }
        return "STOCK";
    }

    private String key(String market, String symbol) {
        return normalize(market) + "|" + normalize(symbol);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
