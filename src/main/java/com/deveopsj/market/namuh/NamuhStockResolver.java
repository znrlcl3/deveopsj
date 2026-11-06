package com.deveopsj.market.namuh;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import lombok.RequiredArgsConstructor;
import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;

@Service @RequiredArgsConstructor
public class NamuhStockResolver {
    private final InvestmentAssetRepository assets;
    private final NamuhStockCatalog catalog;
    public record Resolution(InvestmentAsset asset, String error) {}

    // Repository writes commit separately so concurrent unique-key collisions can be re-read.
    public Resolution resolve(String rawSymbol, String rawCurrency) {
        String currency = rawCurrency.strip().toUpperCase(Locale.ROOT);
        String symbol = rawSymbol.strip().toUpperCase(Locale.ROOT);
        if ("KRW".equals(currency) && symbol.matches("A[0-9A-Z]{6}")) symbol = symbol.substring(1);
        var existing = assets.findByActiveTrueAndSymbolAndCurrency(symbol, currency);
        if (existing.size() == 1) return new Resolution(existing.get(0), "");
        if (!"KRW".equals(currency) || !symbol.matches("[0-9A-Z]{6}"))
            return new Resolution(null, existing.size() > 1
                    ? "같은 코드의 해외 종목이 여러 시장에 있습니다. 거래소 확인이 필요합니다."
                    : "앱 종목 목록에 코드·통화가 일치하는 종목이 없습니다. 종목 동기화 또는 직접 입력이 필요합니다.");
        final Optional<NamuhStockCatalog.Stock> found;
        try { found = catalog.find(symbol); }
        catch (IllegalStateException e) { return new Resolution(null, e.getMessage()); }
        if (found.isEmpty()) return new Resolution(null, "나무 공식 국내 종목 목록에서 코드를 확인하지 못했습니다. 직접 확인이 필요합니다.");
        var stock = found.get();
        if (existing.size() > 1) {
            var verified = existing.stream().filter(asset -> stock.market().equals(asset.getMarket())
                    && stock.assetClass().equals(asset.getAssetClass())).toList();
            if (verified.size() == 1) return new Resolution(verified.get(0), "");
            return new Resolution(null, "나무 공식 시장·종목 유형과 일치하는 등록 종목을 하나로 확인하지 못했습니다. 관리자 확인이 필요합니다.");
        }
        var byMarket = assets.findByMarketAndSymbol(stock.market(), stock.symbol());
        if (byMarket.isPresent()) return check(byMarket.get());
        var asset = new InvestmentAsset();
        asset.setSymbol(stock.symbol()); asset.setAssetName(stock.name()); asset.setMarket(stock.market());
        asset.setAssetClass(stock.assetClass()); asset.setCurrency("KRW"); asset.setActive(true);
        try { return new Resolution(assets.saveAndFlush(asset), ""); }
        catch (DataIntegrityViolationException concurrent) {
            return assets.findByMarketAndSymbol(stock.market(), stock.symbol()).map(this::check)
                    .orElseGet(() -> new Resolution(null, "종목 등록이 겹쳤습니다. 잔고를 다시 조회하세요."));
        }
    }
    private Resolution check(InvestmentAsset asset) {
        return asset.isActive() && "KRW".equals(asset.getCurrency()) ? new Resolution(asset, "")
                : new Resolution(null, "해당 종목이 비활성화되어 있거나 통화 설정이 다릅니다. 관리자 확인이 필요합니다.");
    }
}
