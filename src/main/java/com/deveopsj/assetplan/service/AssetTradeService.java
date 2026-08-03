package com.deveopsj.assetplan.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.dto.AssetTradeUpdateRequest;
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
        AssetTrade trade = new AssetTrade();
        applyTrade(trade, request, member);
        assetTradeRepository.save(trade);
    }

    public void update(AssetTradeUpdateRequest request, Member member) {
        AssetTrade trade = assetTradeRepository
                .findByIdAndAssetPlanMemberMemberId(request.getId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("수정할 매매 내역을 찾을 수 없습니다."));
        applyTrade(trade, request, member);
    }

    public void delete(Long id, Member member) {
        AssetTrade trade = assetTradeRepository
                .findByIdAndAssetPlanMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 매매 내역을 찾을 수 없습니다."));
        assetTradeRepository.delete(trade);
    }

    private void applyTrade(AssetTrade trade, AssetTradeSaveRequest request, Member member) {
        AssetPlan assetPlan = assetPlanRepository
                .findByIdAndMemberMemberId(request.getAssetPlanId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));

        InvestmentAsset investmentAsset = investmentAssetRepository
                .findByIdAndActiveTrue(request.getInvestmentAssetId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 종목을 사용할 수 없습니다."));
        String currency = investmentAsset.getCurrency();

        boolean unitPriceBasis = request.getUnitPrice() != null
                && ("UNIT_PRICE".equals(request.getCalculationBasis()) || request.getTradeAmount() == null);
        BigDecimal unitPrice = unitPriceBasis
                ? request.getUnitPrice().setScale(4, RoundingMode.HALF_UP)
                : request.getTradeAmount().divide(request.getQuantity(), 4, RoundingMode.HALF_UP);
        BigDecimal tradeAmount = unitPriceBasis
                ? unitPrice.multiply(request.getQuantity()).setScale(4, RoundingMode.HALF_UP)
                : request.getTradeAmount();
        if (unitPrice.precision() - unitPrice.scale() > 15) {
            throw new IllegalArgumentException("계산된 체결 단가가 허용 범위를 초과합니다.");
        }
        if (tradeAmount.precision() - tradeAmount.scale() > 15) {
            throw new IllegalArgumentException("계산된 총 매매금액이 허용 범위를 초과합니다.");
        }
        long grossAmount = tradeAmount
                .multiply(request.getExchangeRate())
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
        long settlementAmount = request.getTradeType() == TradeType.BUY
                ? Math.addExact(Math.addExact(grossAmount, request.getFeeKrw()), request.getTaxKrw())
                : Math.subtractExact(Math.subtractExact(grossAmount, request.getFeeKrw()), request.getTaxKrw());
        if (settlementAmount < 0) {
            throw new IllegalArgumentException("수수료와 세금이 거래금액보다 클 수 없습니다.");
        }

        trade.setAssetPlan(assetPlan);
        trade.setInvestmentAsset(investmentAsset);
        trade.setTradeType(request.getTradeType());
        trade.setTradeDate(request.getTradeDate());
        trade.setQuantity(request.getQuantity());
        trade.setUnitPrice(unitPrice);
        trade.setCurrency(currency);
        trade.setExchangeRate(request.getExchangeRate());
        trade.setFeeKrw(request.getFeeKrw());
        trade.setTaxKrw(request.getTaxKrw());
        trade.setSettlementAmountKrw(settlementAmount);
        trade.setMemo(request.getMemo() == null ? null : request.getMemo().trim());
    }

    @Transactional(readOnly = true)
    public List<AssetTrade> getTradesByMember(Member member) {
        return assetTradeRepository
                .findByAssetPlanMemberMemberIdOrderByTradeDateDescIdDesc(member.getMemberId());
    }

    @Transactional(readOnly = true)
    public List<AssetTrade> getTradesByMemberAndMonth(Member member, YearMonth month) {
        return assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateBetweenOrderByTradeDateDescIdDesc(
                        member.getMemberId(), month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<AssetTrade> getTradesByMemberAndPeriod(Member member, LocalDate start, LocalDate end) {
        return assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateBetweenOrderByTradeDateDescIdDesc(
                        member.getMemberId(), start, end);
    }

}
