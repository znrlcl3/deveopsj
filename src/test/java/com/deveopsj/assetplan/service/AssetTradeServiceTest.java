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
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
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
        AssetTradeSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));
        when(investmentAssetRepository.findByMarketAndSymbol("KOSPI", "123456"))
                .thenReturn(Optional.empty());
        when(investmentAssetRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        assetTradeService.save(request, member);

        verify(assetTradeRepository).save(org.mockito.ArgumentMatchers.argThat(trade ->
                trade.getAssetPlan() == plan
                        && trade.getSettlementAmountKrw().equals(235_000L)
                        && trade.getQuantity().compareTo(new BigDecimal("10")) == 0
                        && trade.getInvestmentAsset().getSymbol().equals("123456")));
    }

    @Test
    void 타인의_플랜에는_매매내역을_등록하지_않는다() {
        Member member = member(7L);
        AssetTradeSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetTradeService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(investmentAssetRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(assetTradeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetTradeSaveRequest request(Long assetPlanId) {
        AssetTradeSaveRequest request = new AssetTradeSaveRequest();
        request.setAssetPlanId(assetPlanId);
        request.setSymbol("123456");
        request.setAssetName("ACE 다우100");
        request.setMarket("KOSPI");
        request.setAssetClass("ETF");
        request.setTradeType(TradeType.BUY);
        request.setTradeDate(LocalDate.of(2026, 7, 28));
        request.setQuantity(new BigDecimal("10"));
        request.setUnitPrice(new BigDecimal("23500"));
        request.setCurrency("KRW");
        request.setExchangeRate(BigDecimal.ONE);
        request.setFeeKrw(0L);
        request.setTaxKrw(0L);
        return request;
    }
}
