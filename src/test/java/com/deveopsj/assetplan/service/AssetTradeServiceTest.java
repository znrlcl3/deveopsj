package com.deveopsj.assetplan.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

@ExtendWith(MockitoExtension.class)
class AssetTradeServiceTest {

    @Mock
    private AssetTradeRepository assetTradeRepository;

    @Mock
    private InvestmentAssetRepository investmentAssetRepository;

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @InjectMocks
    private AssetTradeService assetTradeService;

    @Test
    void 매수수량과_체결가로_원화정산금액을_계산한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        InvestmentAsset asset = asset(11L);
        AssetTradeSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));
        when(investmentAssetRepository.findByIdAndActiveTrue(11L)).thenReturn(Optional.of(asset));

        assetTradeService.save(request, member);

        verify(assetTradeRepository).save(org.mockito.ArgumentMatchers.argThat(trade ->
                trade.getAssetPlan() == plan
                        && trade.getSettlementAmountKrw().equals(235_000L)
                        && trade.getQuantity().compareTo(new BigDecimal("10")) == 0
                        && trade.getInvestmentAsset() == asset));
    }

    @Test
    void 타인의_플랜에는_매매내역을_등록하지_않는다() {
        Member member = member(7L);
        AssetTradeSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetTradeService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(investmentAssetRepository, never()).findByIdAndActiveTrue(org.mockito.ArgumentMatchers.any());
        verify(assetTradeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 본인의_매매내역을_수정하면_정산금액을_다시_계산한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        InvestmentAsset asset = asset(11L);
        AssetTrade trade = new AssetTrade();
        AssetTradeUpdateRequest request = updateRequest(9L, 3L);
        request.setQuantity(new BigDecimal("5"));
        request.setTradeAmount(new BigDecimal("117500"));
        when(assetTradeRepository.findByIdAndAssetPlanMemberMemberId(9L, 7L))
                .thenReturn(Optional.of(trade));
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));
        when(investmentAssetRepository.findByIdAndActiveTrue(11L)).thenReturn(Optional.of(asset));

        assetTradeService.update(request, member);

        org.assertj.core.api.Assertions.assertThat(trade.getSettlementAmountKrw()).isEqualTo(117_500L);
        org.assertj.core.api.Assertions.assertThat(trade.getUnitPrice())
                .isEqualByComparingTo(new BigDecimal("23500.0000"));
        org.assertj.core.api.Assertions.assertThat(trade.getAssetPlan()).isSameAs(plan);
        verify(assetTradeRepository, never()).save(trade);
    }

    @Test
    void 타인의_매매내역은_수정하거나_삭제할수없다() {
        Member member = member(7L);
        AssetTradeUpdateRequest request = updateRequest(9L, 3L);
        when(assetTradeRepository.findByIdAndAssetPlanMemberMemberId(9L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetTradeService.update(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> assetTradeService.delete(9L, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetTradeRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetTradeSaveRequest request(Long assetPlanId) {
        AssetTradeSaveRequest request = new AssetTradeSaveRequest();
        request.setAssetPlanId(assetPlanId);
        request.setInvestmentAssetId(11L);
        request.setTradeType(TradeType.BUY);
        request.setTradeDate(LocalDate.of(2026, 7, 28));
        request.setQuantity(new BigDecimal("10"));
        request.setTradeAmount(new BigDecimal("235000"));
        request.setExchangeRate(BigDecimal.ONE);
        request.setFeeKrw(0L);
        request.setTaxKrw(0L);
        return request;
    }

    private AssetTradeUpdateRequest updateRequest(Long id, Long assetPlanId) {
        AssetTradeSaveRequest source = request(assetPlanId);
        AssetTradeUpdateRequest request = new AssetTradeUpdateRequest();
        request.setId(id);
        request.setAssetPlanId(source.getAssetPlanId());
        request.setInvestmentAssetId(source.getInvestmentAssetId());
        request.setTradeType(source.getTradeType());
        request.setTradeDate(source.getTradeDate());
        request.setQuantity(source.getQuantity());
        request.setTradeAmount(source.getTradeAmount());
        request.setExchangeRate(source.getExchangeRate());
        request.setFeeKrw(source.getFeeKrw());
        request.setTaxKrw(source.getTaxKrw());
        return request;
    }

    private InvestmentAsset asset(Long id) {
        InvestmentAsset asset = new InvestmentAsset();
        asset.setId(id);
        asset.setSymbol("123456");
        asset.setAssetName("ACE 다우100");
        asset.setMarket("KOSPI");
        asset.setAssetClass("ETF");
        asset.setCurrency("KRW");
        asset.setActive(true);
        return asset;
    }
}
