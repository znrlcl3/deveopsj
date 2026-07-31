package com.deveopsj.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.cashflow.dto.MonthlyCashFlowSummary;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class MonthlyCashFlowServiceTest {

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private AssetTradeRepository assetTradeRepository;

    @Test
    void 수입부터_납입과_증권계좌매매까지_월간흐름을_계산한다() {
        MonthlyCashFlowService service = new MonthlyCashFlowService(
                incomeRepository, dailySpendingRepository,
                assetSavingsRepository, assetTradeRepository);
        Member member = member(7L);
        YearMonth month = YearMonth.of(2026, 7);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        AssetPlan isa = plan(11L, "ISA 계좌", "ETF");
        AssetPlan pension = plan(12L, "연금저축", "SAVINGS");

        when(incomeRepository.getTotalIncome(7L, start, end)).thenReturn(3_000_000L);
        when(dailySpendingRepository.getTotalSpending(7L, start, end))
                .thenReturn(1_000_000L);
        AssetSavings juneIsaSaving = saving(isa, DepositType.PLAN, 500_000L);
        juneIsaSaving.setDepositDate(LocalDate.of(2026, 6, 10));
        when(assetSavingsRepository.findAllByMemberIdAndDepositDateLessThanEqual(7L, end))
                .thenReturn(List.of(
                        juneIsaSaving,
                        saving(isa, DepositType.PLAN, 1_100_000L),
                        saving(pension, DepositType.PLAN, 300_000L),
                        saving(null, DepositType.EXTRA, 100_000L)));
        AssetTrade juneBuy = trade(isa, TradeType.BUY, 400_000L);
        juneBuy.setTradeDate(LocalDate.of(2026, 6, 15));
        when(assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        7L, end))
                .thenReturn(List.of(
                        juneBuy,
                        trade(isa, TradeType.BUY, 1_000_000L),
                        trade(isa, TradeType.SELL, 100_000L)));

        MonthlyCashFlowSummary summary = service.getMonthlyCashFlow(member, month);

        assertThat(summary.totalIncomeKrw()).isEqualTo(3_000_000L);
        assertThat(summary.totalSpendingKrw()).isEqualTo(1_000_000L);
        assertThat(summary.totalDepositKrw()).isEqualTo(1_500_000L);
        assertThat(summary.remainingCashKrw()).isEqualTo(500_000L);
        assertThat(summary.securitiesDepositKrw()).isEqualTo(1_100_000L);
        assertThat(summary.totalBuyKrw()).isEqualTo(1_000_000L);
        assertThat(summary.totalSellKrw()).isEqualTo(100_000L);
        assertThat(summary.netPurchaseKrw()).isEqualTo(900_000L);
        assertThat(summary.uninvestedSecuritiesCashKrw()).isEqualTo(200_000L);
        assertThat(summary.estimatedSecuritiesAccountCashKrw()).isEqualTo(300_000L);
        assertThat(summary.planFlows()).singleElement().satisfies(flow -> {
            assertThat(flow.planName()).isEqualTo("ISA 계좌");
            assertThat(flow.uninvestedAmountKrw()).isEqualTo(200_000L);
            assertThat(flow.cumulativeDepositAmountKrw()).isEqualTo(1_600_000L);
            assertThat(flow.cumulativeNetPurchaseAmountKrw()).isEqualTo(1_300_000L);
            assertThat(flow.estimatedAccountCashKrw()).isEqualTo(300_000L);
        });
        verify(assetSavingsRepository)
                .findAllByMemberIdAndDepositDateLessThanEqual(7L, end);
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetPlan plan(Long id, String name, String assetType) {
        Goal goal = new Goal();
        goal.setTitle("1억 모으기");
        AssetPlan plan = new AssetPlan();
        plan.setId(id);
        plan.setPlanName(name);
        plan.setAssetType(assetType);
        plan.setGoal(goal);
        return plan;
    }

    private AssetSavings saving(AssetPlan plan, DepositType type, long amount) {
        return AssetSavings.builder()
                .assetPlan(plan)
                .depositType(type)
                .amount(amount)
                .depositDate(LocalDate.of(2026, 7, 10))
                .build();
    }

    private AssetTrade trade(AssetPlan plan, TradeType type, long settlementAmount) {
        AssetTrade trade = new AssetTrade();
        trade.setAssetPlan(plan);
        trade.setTradeType(type);
        trade.setTradeDate(LocalDate.of(2026, 7, 15));
        trade.setQuantity(BigDecimal.ONE);
        trade.setUnitPrice(BigDecimal.valueOf(settlementAmount));
        trade.setExchangeRate(BigDecimal.ONE);
        trade.setSettlementAmountKrw(settlementAmount);
        trade.setFeeKrw(0L);
        trade.setTaxKrw(0L);
        return trade;
    }
}
