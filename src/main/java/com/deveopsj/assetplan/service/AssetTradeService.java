package com.deveopsj.assetplan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetTradeService {

    private final AssetTradeRepository assetTradeRepository;
    private final InvestmentAssetRepository investmentAssetRepository;
    private final AssetPlanRepository assetPlanRepository;

    public void save(AssetTradeSaveRequest request, Member member) {
        AssetPlan assetPlan = assetPlanRepository
                .findByIdAndMemberMemberId(request.getAssetPlanId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));

        String market = normalizeCode(request.getMarket());
        String symbol = normalizeCode(request.getSymbol());
        String currency = normalizeCode(request.getCurrency());
        InvestmentAsset investmentAsset = investmentAssetRepository.findByMarketAndSymbol(market, symbol)
                .orElseGet(() -> investmentAssetRepository.save(
                        newInvestmentAsset(request, market, symbol, currency)));

        long grossAmount = request.getQuantity()
                .multiply(request.getUnitPrice())
                .multiply(request.getExchangeRate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long settlementAmount = request.getTradeType() == TradeType.BUY
                ? Math.addExact(Math.addExact(grossAmount, request.getFeeKrw()), request.getTaxKrw())
                : Math.subtractExact(Math.subtractExact(grossAmount, request.getFeeKrw()), request.getTaxKrw());
        if (settlementAmount < 0) {
            throw new IllegalArgumentException("수수료와 세금이 거래금액보다 클 수 없습니다.");
        }

        AssetTrade trade = new AssetTrade();
        trade.setAssetPlan(assetPlan);
        trade.setInvestmentAsset(investmentAsset);
        trade.setTradeType(request.getTradeType());
        trade.setTradeDate(request.getTradeDate());
        trade.setQuantity(request.getQuantity());
        trade.setUnitPrice(request.getUnitPrice());
        trade.setCurrency(currency);
        trade.setExchangeRate(request.getExchangeRate());
        trade.setFeeKrw(request.getFeeKrw());
        trade.setTaxKrw(request.getTaxKrw());
        trade.setSettlementAmountKrw(settlementAmount);
        trade.setMemo(request.getMemo() == null ? null : request.getMemo().trim());
        assetTradeRepository.save(trade);
    }

    @Transactional(readOnly = true)
    public List<AssetTrade> getTradesByMember(Member member) {
        return assetTradeRepository
                .findByAssetPlanMemberMemberIdOrderByTradeDateDescIdDesc(member.getMemberId());
    }

    private InvestmentAsset newInvestmentAsset(AssetTradeSaveRequest request,
            String market, String symbol, String currency) {
        InvestmentAsset asset = new InvestmentAsset();
        asset.setMarket(market);
        asset.setSymbol(symbol);
        asset.setAssetName(request.getAssetName().trim());
        asset.setAssetClass(normalizeCode(request.getAssetClass()));
        asset.setCurrency(currency);
        asset.setActive(true);
        return asset;
    }

    private String normalizeCode(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
