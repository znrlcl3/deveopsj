package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.market.dto.MarketPriceQuote;
import com.deveopsj.market.dto.MonthlyPortfolioSummary;
import com.deveopsj.market.dto.PortfolioSummary;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class PortfolioValuationServiceTest {

    @Mock
    private AssetTradeRepository assetTradeRepository;

    @Mock
    private KisMarketPriceService kisMarketPriceService;

    @Test
    void 매수와매도를_이동평균법으로_계산해_평가손익을_반환한다() {
        Member member = member(7L);
        AssetPlan plan = plan(3L);
        InvestmentAsset asset = asset(11L);
        List<AssetTrade> trades = List.of(
                trade(plan, asset, TradeType.BUY, "10", "100", 1_000L, 1),
                trade(plan, asset, TradeType.BUY, "10", "200", 2_000L, 2),
                trade(plan, asset, TradeType.SELL, "5", "210", 1_050L, 3));
        when(assetTradeRepository
                .findByAssetPlanMemberMemberIdOrderByTradeDateAscIdAsc(7L))
                .thenReturn(trades);
        when(kisMarketPriceService.getQuote(11L)).thenReturn(new MarketPriceQuote(
                11L, "005930", "삼성전자", "KOSPI", "KRW",
                new BigDecimal("200"), new BigDecimal("190"),
                new BigDecimal("10"), new BigDecimal("5.26"), LocalDateTime.now(),
                false, false));
        PortfolioValuationService service = new PortfolioValuationService(
                assetTradeRepository, kisMarketPriceService);

        PortfolioSummary summary = service.getPortfolio(member);

        assertThat(summary.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualByComparingTo(new BigDecimal("15"));
            assertThat(position.averageUnitPrice()).isEqualByComparingTo(new BigDecimal("150"));
            assertThat(position.remainingCostKrw()).isEqualTo(2_250L);
            assertThat(position.valuationAmountKrw()).isEqualTo(3_000L);
            assertThat(position.profitLossKrw()).isEqualTo(750L);
            assertThat(position.returnRate()).isEqualByComparingTo(new BigDecimal("33.33"));
        });
        assertThat(summary.totalProfitLossKrw()).isEqualTo(750L);
    }

    @Test
    void 한종목의_현재가조회실패가_전체보유자산조회를_중단하지않는다() {
        Member member = member(7L);
        AssetPlan plan = plan(3L);
        InvestmentAsset asset = asset(11L);
        when(assetTradeRepository
                .findByAssetPlanMemberMemberIdOrderByTradeDateAscIdAsc(7L))
                .thenReturn(List.of(trade(plan, asset, TradeType.BUY, "2", "100", 200L, 1)));
        when(kisMarketPriceService.getQuote(11L))
                .thenThrow(new IllegalStateException("현재가 없음"));
        PortfolioValuationService service = new PortfolioValuationService(
                assetTradeRepository, kisMarketPriceService);

        PortfolioSummary summary = service.getPortfolio(member);

        assertThat(summary.positions()).singleElement().satisfies(position -> {
            assertThat(position.quoteAvailable()).isFalse();
            assertThat(position.quoteError()).isEqualTo("현재가 없음");
        });
        assertThat(summary.totalValuationKrw()).isZero();
        assertThat(summary.totalProfitLossKrw()).isZero();
    }

    @Test
    void 선택월의_거래금액과_월말누적보유자산을_계산한다() {
        Member member = member(7L);
        AssetPlan plan = plan(3L);
        InvestmentAsset asset = asset(11L);
        AssetTrade juneBuy = trade(
                plan, asset, TradeType.BUY, "10", "100", 1_000L, 1);
        juneBuy.setTradeDate(LocalDate.of(2026, 6, 10));
        AssetTrade julyBuy = trade(
                plan, asset, TradeType.BUY, "5", "150", 750L, 2);
        julyBuy.setTradeDate(LocalDate.of(2026, 7, 5));
        AssetTrade julySell = trade(
                plan, asset, TradeType.SELL, "3", "120", 360L, 3);
        julySell.setTradeDate(LocalDate.of(2026, 7, 20));
        when(assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        7L, LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(juneBuy, julyBuy, julySell));
        PortfolioValuationService service = new PortfolioValuationService(
                assetTradeRepository, kisMarketPriceService);

        MonthlyPortfolioSummary summary = service.getMonthlyPortfolio(
                member, YearMonth.of(2026, 7));

        assertThat(summary.monthlyBuyAmountKrw()).isEqualTo(750L);
        assertThat(summary.monthlySellAmountKrw()).isEqualTo(360L);
        assertThat(summary.monthlyNetInvestmentKrw()).isEqualTo(390L);
        assertThat(summary.monthEndCostKrw()).isEqualTo(1_400L);
        assertThat(summary.positions()).singleElement().satisfies(position -> {
            assertThat(position.quantity()).isEqualByComparingTo("12");
            assertThat(position.averageUnitPrice()).isEqualByComparingTo("116.6667");
            assertThat(position.remainingCostKrw()).isEqualTo(1_400L);
        });
        verify(assetTradeRepository)
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        7L, LocalDate.of(2026, 7, 31));
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetPlan plan(Long id) {
        Goal goal = new Goal();
        goal.setTitle("1억 모으기");
        AssetPlan plan = new AssetPlan();
        plan.setId(id);
        plan.setPlanName("ISA 계좌");
        plan.setGoal(goal);
        return plan;
    }

    private InvestmentAsset asset(Long id) {
        InvestmentAsset asset = new InvestmentAsset();
        asset.setId(id);
        asset.setSymbol("005930");
        asset.setAssetName("삼성전자");
        asset.setMarket("KOSPI");
        asset.setCurrency("KRW");
        asset.setAssetClass("STOCK");
        return asset;
    }

    private AssetTrade trade(AssetPlan plan, InvestmentAsset asset, TradeType type,
            String quantity, String unitPrice, long settlementAmount, long id) {
        AssetTrade trade = new AssetTrade();
        trade.setId(id);
        trade.setAssetPlan(plan);
        trade.setInvestmentAsset(asset);
        trade.setTradeType(type);
        trade.setTradeDate(LocalDate.of(2026, 7, (int) id));
        trade.setQuantity(new BigDecimal(quantity));
        trade.setUnitPrice(new BigDecimal(unitPrice));
        trade.setCurrency(asset.getCurrency());
        trade.setExchangeRate(BigDecimal.ONE);
        trade.setSettlementAmountKrw(settlementAmount);
        trade.setFeeKrw(0L);
        trade.setTaxKrw(0L);
        return trade;
    }
}
