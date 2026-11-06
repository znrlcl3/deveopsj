package com.deveopsj.assetplan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.deveopsj.assetplan.dto.HoldingSaveRequest;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.repository.*;
import com.deveopsj.member.entity.Member;

@Service @Validated @RequiredArgsConstructor
public class HoldingService {
    private final AssetPlanRepository plans;
    private final InvestmentAssetRepository assets;
    private final AssetTradeRepository trades;

    @Transactional
    public void save(@Valid HoldingSaveRequest request, Member member) {
        var plan = plans.lockOwned(request.getAssetPlanId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 플랜을 사용할 수 없습니다."));
        var asset = assets.findByIdAndActiveTrue(request.getInvestmentAssetId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 종목을 사용할 수 없습니다."));
        var rate = "KRW".equals(asset.getCurrency()) ? BigDecimal.ONE : request.getExchangeRate();
        if (rate == null || rate.signum() <= 0) {
            throw new IllegalArgumentException("해외 종목은 원화 매입원금 계산에 사용할 매입 환율을 입력해 주세요.");
        }
        long cost = request.getQuantity().multiply(request.getAveragePrice()).multiply(rate)
                .setScale(0, RoundingMode.HALF_UP).longValueExact();
        var trade = new AssetTrade();
        trade.setAssetPlan(plan); trade.setInvestmentAsset(asset);
        trade.setTradeType(AssetTrade.TradeType.SNAPSHOT); trade.setTradeDate(LocalDate.now());
        trade.setQuantity(request.getQuantity()); trade.setUnitPrice(request.getAveragePrice());
        trade.setCurrency(asset.getCurrency()); trade.setExchangeRate(rate);
        trade.setFeeKrw(0L); trade.setTaxKrw(0L); trade.setSettlementAmountKrw(cost);
        trade.setMemo("현재 보유분 직접 등록");
        trades.save(trade);
    }
}
