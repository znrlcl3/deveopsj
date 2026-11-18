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
    @Test
    void livingCashExcludesInvestmentIncomeAndFutureSpendingWithoutDoubleCountingPending() {
        var today = LocalDate.now();
        var month = YearMonth.from(today);
        var member = member(7L);
        var service = new MonthlyCashFlowService(incomeRepository, dailySpendingRepository,
                assetSavingsRepository, recurringExpenseService);
        when(incomeRepository.getTotalIncome(7L, month.atDay(1), month.atEndOfMonth())).thenReturn(150000L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.DIVIDEND,
                month.atDay(1), month.atEndOfMonth())).thenReturn(100000L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.INTEREST,
                month.atDay(1), month.atEndOfMonth())).thenReturn(20000L);
        var rule = com.deveopsj.spending.entity.RecurringExpense.builder().amount(30900L).build();
        var pending = com.deveopsj.spending.entity.RecurringExpense.builder().amount(5000L).build();
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                7L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of(
                com.deveopsj.spending.entity.DailySpending.builder().spendingDate(today).amount(10000L).build(),
                com.deveopsj.spending.entity.DailySpending.builder().spendingDate(today.plusDays(1))
                        .amount(30900L).recurringExpense(rule).build()));
        when(recurringExpenseService.getOccurrences(member, month)).thenReturn(List.of(
                new com.deveopsj.spending.dto.RecurringExpenseOccurrence(rule, today.plusDays(1), true),
                new com.deveopsj.spending.dto.RecurringExpenseOccurrence(pending, today.plusDays(1), false)));
        var result = service.getMonthlyCashFlow(member, month);
        assertThat(result.livingIncomeKrw()).isEqualTo(30000);
        assertThat(result.investmentIncomeKrw()).isEqualTo(120000);
        assertThat(result.totalSpendingKrw()).isEqualTo(10000);
        assertThat(result.fixedExpenseKrw()).isZero();
        assertThat(result.futureSpendingKrw()).isEqualTo(30900);
        assertThat(result.futureSpendingCount()).isEqualTo(1);
        assertThat(result.incomeSpendingDifferenceKrw()).isEqualTo(20000);
        assertThat(result.remainingCashKrw()).isEqualTo(20000);
        assertThat(result.scheduledTotalKrw()).isEqualTo(5000);
        assertThat(result.afterScheduledCashKrw()).isEqualTo(-15900);
    }


    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private AssetTradeRepository assetTradeRepository;
    @Mock
    private com.deveopsj.spending.service.RecurringExpenseService recurringExpenseService;

    @Test
    void 수입부터_납입과_증권계좌매매까지_월간흐름을_계산한다() {
        MonthlyCashFlowService service = new MonthlyCashFlowService(
                incomeRepository, dailySpendingRepository,
                assetSavingsRepository, recurringExpenseService);
        Member member = member(7L);
        YearMonth month = YearMonth.of(2026, 7);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        AssetPlan isa = plan(11L, "ISA 계좌", "ETF");
        AssetPlan pension = plan(12L, "연금저축", "SAVINGS");

        when(incomeRepository.getTotalIncome(7L, start, end)).thenReturn(3_000_000L);
        var fixedRule = com.deveopsj.spending.entity.RecurringExpense.builder().amount(200_000L).build();
        var installmentRule = com.deveopsj.spending.entity.RecurringExpense.builder()
                .amount(50_000L).cardInstallmentKey("installment").build();
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, start, end))
                .thenReturn(List.of(
                        com.deveopsj.spending.entity.DailySpending.builder().amount(600_000L).build(),
                        com.deveopsj.spending.entity.DailySpending.builder().amount(300_000L).recurringExpense(fixedRule).build(),
                        com.deveopsj.spending.entity.DailySpending.builder().amount(100_000L).recurringExpense(installmentRule)
                                .importSource("CARD_INSTALLMENT").build()));
        when(recurringExpenseService.getOccurrences(member, month)).thenReturn(List.of(
                new com.deveopsj.spending.dto.RecurringExpenseOccurrence(fixedRule, start, false),
                new com.deveopsj.spending.dto.RecurringExpenseOccurrence(installmentRule, start, false),
                new com.deveopsj.spending.dto.RecurringExpenseOccurrence(fixedRule, start, true)));
        AssetSavings juneIsaSaving = saving(isa, DepositType.PLAN, 500_000L);
        juneIsaSaving.setDepositDate(LocalDate.of(2026, 6, 10));
        when(assetSavingsRepository.findAllByMemberIdAndDepositDateLessThanEqual(7L, end))
                .thenReturn(List.of(
                        juneIsaSaving,
                        saving(isa, DepositType.PLAN, 1_100_000L),
                        saving(pension, DepositType.PLAN, 300_000L),
                        saving(null, DepositType.EXTRA, 100_000L)));
        MonthlyCashFlowSummary summary = service.getMonthlyCashFlow(member, month);

        assertThat(summary.totalIncomeKrw()).isEqualTo(3_000_000L);
        assertThat(summary.totalSpendingKrw()).isEqualTo(1_000_000L);
        assertThat(summary.totalDepositKrw()).isEqualTo(1_500_000L);
        assertThat(summary.remainingCashKrw()).isEqualTo(500_000L);
        assertThat(summary.consumptionKrw()).isEqualTo(700_000L);
        assertThat(summary.fixedExpenseKrw()).isEqualTo(300_000L);
        assertThat(summary.otherDepositKrw()).isEqualTo(400_000L);
        assertThat(summary.scheduledTotalKrw()).isEqualTo(250_000L);
        assertThat(summary.afterScheduledCashKrw()).isEqualTo(250_000L);
        assertThat(summary.securitiesDepositKrw()).isEqualTo(1_100_000L);
        assertThat(summary.totalBuyKrw()).isZero();
        assertThat(summary.totalSellKrw()).isZero();
        assertThat(summary.planFlows()).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(assetTradeRepository);
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
