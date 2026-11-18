package com.deveopsj.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetValuation;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.assetplan.repository.AssetValuationRepository;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Test
    void futureSpendingIsExcludedFromActualTotalsCategoriesAndRecentRows() {
        var today = LocalDate.now();
        var month = java.time.YearMonth.from(today);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                7L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of(
                DailySpending.builder().spendingDate(today).categoryCode("FOOD").amount(10000L).build(),
                DailySpending.builder().spendingDate(today.plusDays(1)).categoryCode("FUTURE").amount(30900L).build()));
        var summary = dashboardService.getMonthlySummary(7L, month);
        assertThat(summary.getTotalSpending()).isEqualTo(10000);
        assertThat(summary.getFutureSpending()).isEqualTo(30900);
        assertThat(summary.getFutureSpendingCount()).isEqualTo(1);
        assertThat(summary.getIncomeSpendingDifference()).isEqualTo(-10000);
        assertThat(summary.getSpendingCount()).isEqualTo(1);
        assertThat(summary.getSpendingByCategory()).containsOnlyKeys("FOOD");
        assertThat(summary.getRecentSpendings()).singleElement().satisfies(row ->
                assertThat(row.spendingDate()).isEqualTo(today));
    }

    @Test
    void investmentIncomeAndExpectedSalaryDoNotFundLivingExpenses() {
        var month = java.time.YearMonth.now();
        var previous = month.minusMonths(1);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.SALARY,
                month.atDay(1), month.atEndOfMonth())).thenReturn(0L);
        when(incomeRepository.getTotalIncome(7L, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(150_000L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.DIVIDEND,
                month.atDay(1), month.atEndOfMonth())).thenReturn(100_000L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.INTEREST,
                month.atDay(1), month.atEndOfMonth())).thenReturn(20_000L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.SALARY,
                previous.atDay(1), previous.atEndOfMonth())).thenReturn(3_000_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                7L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of(
                        DailySpending.builder().categoryCode("FOOD").amount(50_000L).build()));
        when(assetSavingsRepository.getTotalByMemberIdAndTypeAndDepositDateBetween(
                7L, com.deveopsj.assetplan.entity.AssetSavings.DepositType.EXTRA,
                month.atDay(1), month.atEndOfMonth())).thenReturn(10_000L);

        var summary = dashboardService.getMonthlySummary(7L, month);
        assertThat(summary.getTotalIncome()).isEqualTo(150_000L);
        assertThat(summary.getInvestmentIncome()).isEqualTo(120_000L);
        assertThat(summary.getLivingIncome()).isEqualTo(30_000L);
        assertThat(summary.getIncomeSpendingDifference()).isEqualTo(-20_000L);
        assertThat(summary.getAvailableCash()).isEqualTo(-30_000L);
        assertThat(summary.getExpectedSalary()).isEqualTo(3_000_000L);
        assertThat(summary.getExpectedSalaryDate()).isEqualTo(month.atEndOfMonth());
        assertThat(summary.getSavingsRate()).isCloseTo(100.0 / 3, org.assertj.core.data.Offset.offset(.0001));
    }

    @Test
    void registeredSalarySuppressesForecast() {
        var month = java.time.YearMonth.now();
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.DIVIDEND,
                month.atDay(1), month.atEndOfMonth())).thenReturn(0L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.INTEREST,
                month.atDay(1), month.atEndOfMonth())).thenReturn(0L);
        when(incomeRepository.getTotalByType(7L, com.deveopsj.income.entity.Income.IncomeType.SALARY,
                month.atDay(1), month.atEndOfMonth())).thenReturn(3_000_000L);
        when(incomeRepository.getTotalIncome(7L, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(3_000_000L);
        var summary = dashboardService.getMonthlySummary(7L, month);
        assertThat(summary.getSalaryIncome()).isEqualTo(3_000_000L);
        assertThat(summary.getExpectedSalary()).isZero();
        assertThat(summary.getExpectedSalaryDate()).isNull();
        org.mockito.Mockito.verify(incomeRepository, org.mockito.Mockito.never()).getTotalByType(
                7L, com.deveopsj.income.entity.Income.IncomeType.SALARY,
                month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
    }

    @Test
    void missingSalaryHistoryAndPastMonthsDoNotInventExpectedIncome() {
        var current = dashboardService.getMonthlySummary(7L, java.time.YearMonth.now());
        assertThat(current.getExpectedSalary()).isZero();
        assertThat(current.getExpectedSalaryDate()).isNull();
        var past = java.time.YearMonth.of(2024, 2);
        var summary = dashboardService.getMonthlySummary(7L, past);
        assertThat(summary.getExpectedSalary()).isZero();
        org.mockito.Mockito.verify(incomeRepository, org.mockito.Mockito.never()).getTotalByType(
                7L, com.deveopsj.income.entity.Income.IncomeType.SALARY,
                past.minusMonths(1).atDay(1), past.minusMonths(1).atEndOfMonth());
    }

    @Mock private com.deveopsj.assetplan.service.BalanceSnapshotService balances;

    @Test
    void 선택월의_실제지출을_소비와_고정으로_나누고_할부는_소비에_포함한다() {
        var month = java.time.YearMonth.of(2024, 2);
        var rule = new com.deveopsj.spending.entity.RecurringExpense();
        rule.setAmount(999_999L);
        when(assetPlanRepository.findByMemberMemberId(7L)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                7L, month.atDay(1), month.atEndOfMonth())).thenReturn(List.of(
                        DailySpending.builder().categoryCode("FOOD").amount(10_000L).build(),
                        DailySpending.builder().categoryCode("FOOD").amount(20_000L).recurringExpense(rule).build(),
                        DailySpending.builder().categoryCode("FOOD").amount(30_000L).recurringExpense(rule)
                                .importSource("CARD_INSTALLMENT").build()));

        var summary = dashboardService.getMonthlySummary(7L, month);
        assertThat(summary.getTotalSpending()).isEqualTo(60_000L);
        assertThat(summary.getConsumptionAmount()).isEqualTo(40_000L);
        assertThat(summary.getFixedExpenseAmount()).isEqualTo(20_000L);
        assertThat(summary.getAvailableCash()).isEqualTo(-60_000L);
    }

    @Test
    void selectedLeapMonthSortsCategoriesAndLimitsRecentRowsWithinMember() {
        var month = java.time.YearMonth.of(2024, 2);
        var start = month.atDay(1);
        var end = month.atEndOfMonth();
        var rows = java.util.stream.IntStream.rangeClosed(1, 6).mapToObj(i ->
                DailySpending.builder().id((long) i).spendingDate(start.plusDays(i))
                        .amount(i * 1000L).categoryCode(i == 6 ? "FOOD" : "TRANS")
                        .memo("내역 " + i).build()).toList();
        when(assetPlanRepository.findByMemberMemberId(7L)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, start, end))
                .thenReturn(rows);
        when(incomeRepository.getTotalIncome(7L, start, end)).thenReturn(30000L);
        var summary = dashboardService.getMonthlySummary(7L, month);
        assertThat(end).isEqualTo(LocalDate.of(2024, 2, 29));
        assertThat(summary.getTotalSpending()).isEqualTo(21000L);
        assertThat(summary.getAvailableCash()).isEqualTo(9000L);
        assertThat(summary.getSpendingCount()).isEqualTo(6);
        assertThat(summary.getSpendingByCategory().keySet()).containsExactly("TRANS", "FOOD");
        assertThat(summary.getRecentSpendings()).hasSize(5);
        assertThat(summary.getRecentSpendings().get(0).memo()).isEqualTo("내역 6");
        assertThat(summary.getRecentSpendings().get(4).memo()).isEqualTo("내역 2");
        verify(incomeRepository).getTotalIncome(7L, start, end);
        verify(assetSavingsRepository).getTotalByMemberIdAndTypeAndDepositDateBetween(
                7L, com.deveopsj.assetplan.entity.AssetSavings.DepositType.EXTRA, start, end);
    }

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private AssetTradeRepository assetTradeRepository;

    @Mock
    private AssetValuationRepository assetValuationRepository;

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private MasterCodeService masterCodeService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void 이번달_적립액만_월간요약에_집계한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());

        Goal goal = new Goal();
        goal.setTitle("비상금");

        AssetPlan plan = new AssetPlan();
        plan.setId(11L);
        plan.setGoal(goal);
        plan.setAssetType("SAVINGS");
        plan.setMonthlyAmount(500_000L);

        MasterCodeDto savingsCode = MasterCodeDto.builder()
                .groupId("ASSET_TYPE")
                .codeId("SAVINGS")
                .codeName("예적금")
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of(plan));
        when(masterCodeService.getAllActiveCodesGrouped())
                .thenReturn(Map.of("ASSET_TYPE", List.of(savingsCode)));
        when(assetSavingsRepository.getTotalSavingsByPlanIdAndDepositDateBetween(
                plan.getId(), start, end)).thenReturn(200_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalInvestmentTarget()).isEqualTo(500_000L);
        assertThat(summary.getTotalInvestment()).isEqualTo(200_000L);
        assertThat(summary.getInvestmentProgress()).isEqualTo(40.0);
        assertThat(summary.getPlanProgressList()).singleElement().satisfies(progress -> {
            assertThat(progress.getActualAmount()).isEqualTo(200_000L);
            assertThat(progress.getProgress()).isEqualTo(40.0);
        });
        verify(assetSavingsRepository).getTotalSavingsByPlanIdAndDepositDateBetween(
                plan.getId(), start, end);
    }

    @Test
    void 지출카테고리를_활성_마스터코드의_한글명으로_집계한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());
        MasterCodeDto foodCode = MasterCodeDto.builder()
                .groupId("SPENDING_CAT")
                .codeId("FOOD")
                .codeName("식비")
                .build();
        DailySpending lunch = DailySpending.builder()
                .categoryCode("FOOD")
                .amount(12_000L)
                .build();
        DailySpending dinner = DailySpending.builder()
                .categoryCode("FOOD")
                .amount(18_000L)
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped())
                .thenReturn(Map.of("SPENDING_CAT", List.of(foodCode)));
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of(lunch, dinner));

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalInvestmentTarget()).isZero();
        assertThat(summary.getInvestmentProgress()).isZero();
        assertThat(summary.getSpendingByCategory())
                .containsExactly(Map.entry("식비", 30_000L));
    }

    @Test
    void 기타추가납입을_이번달_실제납입원금에_포함한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(assetSavingsRepository.getTotalByMemberIdAndTypeAndDepositDateBetween(
                memberId, com.deveopsj.assetplan.entity.AssetSavings.DepositType.EXTRA, start, end))
                .thenReturn(300_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalInvestment()).isEqualTo(300_000L);
        assertThat(summary.getExtraInvestment()).isEqualTo(300_000L);
        assertThat(summary.getInvestmentProgress()).isZero();
    }

    @Test
    void 대시보드는_증권평가없이_실제납입만_집계한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());

        Goal goal = new Goal();
        goal.setTitle("장기 투자");

        AssetPlan plan = new AssetPlan();
        plan.setId(11L);
        plan.setGoal(goal);
        plan.setAssetType("STOCK");
        plan.setMonthlyAmount(500_000L);

        AssetValuation latestValuation = AssetValuation.builder()
                .assetPlan(plan)
                .valuationAmount(1_150_000L)
                .valuationDate(today)
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of(plan));
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(assetSavingsRepository.getTotalSavingsByPlanIdAndDepositDateBetween(
                plan.getId(), start, end)).thenReturn(200_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalInvestment()).isEqualTo(200_000L);
        assertThat(summary.getValuationPrincipal()).isZero();
        assertThat(summary.getCurrentValuation()).isZero();
        assertThat(summary.getValuationProfit()).isZero();
        assertThat(summary.getValuedPlanCount()).isZero();
        org.mockito.Mockito.verifyNoInteractions(assetTradeRepository, assetValuationRepository, balances);
    }

    @Test
    void 이번달_수입으로_저축률과_가용현금을_계산한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());
        DailySpending spending = DailySpending.builder()
                .categoryCode("FOOD")
                .amount(500_000L)
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(assetSavingsRepository.getTotalByMemberIdAndTypeAndDepositDateBetween(
                memberId, com.deveopsj.assetplan.entity.AssetSavings.DepositType.EXTRA, start, end))
                .thenReturn(1_000_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of(spending));
        when(incomeRepository.getTotalIncome(memberId, start, end)).thenReturn(3_000_000L);

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalIncome()).isEqualTo(3_000_000L);
        assertThat(summary.getTotalSpending()).isEqualTo(500_000L);
        assertThat(summary.getSavingsRate()).isCloseTo(
                100.0 / 3.0, org.assertj.core.data.Offset.offset(0.0001));
        assertThat(summary.getAvailableCash()).isEqualTo(1_500_000L);
    }
}
