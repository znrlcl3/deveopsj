package com.deveopsj.dashboard.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

import com.deveopsj.common.service.MasterCodeService;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {


    private final AssetPlanRepository assetPlanRepository;
    private final AssetSavingsRepository assetSavingsRepository;
    private final DailySpendingRepository dailySpendingRepository;
    private final IncomeRepository incomeRepository;
    private final MasterCodeService masterCodeService;

    public DashboardSummary getMonthlySummary(Long memberId) {
        return getMonthlySummary(memberId, YearMonth.now());
    }

    public DashboardSummary getMonthlySummary(Long memberId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        // 1. 자산 플랜 및 이행률 계산
        List<AssetPlan> plans = assetPlanRepository.findByMemberMemberId(memberId);
        List<DashboardSummary.PlanProgressDto> planProgressList = new ArrayList<>();
        
        long totalTarget = 0;
        long totalActual = 0;
        long valuationPrincipal = 0;
        long currentValuation = 0;
        long sourceProfitAdjustment=0;
        int valuedPlanCount = 0;
        
        var activeCodeGroups = masterCodeService.getAllActiveCodesGrouped();
        Map<String, String> assetTypeMap = activeCodeGroups.getOrDefault("ASSET_TYPE", List.of())
            .stream().collect(Collectors.toMap(c -> c.getCodeId(), c -> c.getCodeName()));
        Map<String, String> spendingCategoryMap = activeCodeGroups
            .getOrDefault("SPENDING_CAT", List.of())
            .stream().collect(Collectors.toMap(c -> c.getCodeId(), c -> c.getCodeName()));

        for (AssetPlan plan : plans) {
            totalTarget += plan.getMonthlyAmount();
            Long savings = assetSavingsRepository.getTotalSavingsByPlanIdAndDepositDateBetween(
                    plan.getId(), start, end);
            long actual = savings != null ? savings : 0;
            totalActual += actual;

            double progress = (plan.getMonthlyAmount() == 0) ? 0 : (actual / (double)plan.getMonthlyAmount()) * 100;
            
            planProgressList.add(DashboardSummary.PlanProgressDto.builder()
                .goalTitle(plan.getGoal()==null ? "미연결 목표" : plan.getGoal().getTitle())
                .assetType(assetTypeMap.getOrDefault(plan.getAssetType(), plan.getAssetType()))
                .monthlyAmount(plan.getMonthlyAmount())
                .actualAmount(actual)
                .progress(Math.max(0, Math.min(progress, 100.0)))
                .build());
        }

        Long extraInvestmentValue = assetSavingsRepository.getTotalByMemberIdAndTypeAndDepositDateBetween(
                memberId, DepositType.EXTRA, start, end);
        long extraInvestment = extraInvestmentValue != null ? extraInvestmentValue : 0;
        totalActual = Math.addExact(totalActual, extraInvestment);

        // 2. 이번 달 지출 내역 계산
        var today = LocalDate.now();
        var monthlySpendings = dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(memberId, start, end);
        var futureSpendings = monthlySpendings.stream()
                .filter(s -> s.getSpendingDate() != null && s.getSpendingDate().isAfter(today)).toList();
        var spendings = monthlySpendings.stream()
                .filter(s -> s.getSpendingDate() == null || !s.getSpendingDate().isAfter(today)).toList();
        Long totalSpend = spendings.stream().mapToLong(s -> s.getAmount()).sum();
        long fixedExpenseAmount = spendings.stream().filter(s -> s.isFixedExpense())
                .mapToLong(s -> s.getAmount()).sum();
        Long totalIncomeValue = incomeRepository.getTotalIncome(memberId, start, end);
        long totalIncome = totalIncomeValue != null ? totalIncomeValue : 0;
        long dividendIncome = incomeRepository.getTotalByType(memberId, IncomeType.DIVIDEND, start, end);
        long interestIncome = incomeRepository.getTotalByType(memberId, IncomeType.INTEREST, start, end);
        long investmentIncome = Math.addExact(dividendIncome, interestIncome);
        long livingIncome = Math.subtractExact(totalIncome, investmentIncome);
        long salaryIncome = incomeRepository.getTotalByType(memberId, IncomeType.SALARY, start, end);
        // Forecast only: never create an income record or add it to actual cash flow.
        long expectedSalary = 0;
        if (!month.isBefore(YearMonth.now()) && salaryIncome == 0) {
            var previousMonth = month.minusMonths(1);
            expectedSalary = incomeRepository.getTotalByType(memberId, IncomeType.SALARY,
                    previousMonth.atDay(1), previousMonth.atEndOfMonth());
        }
        
        var categoryMap = spendings.stream()
                .collect(Collectors.groupingBy(
                        s -> spendingCategoryMap.getOrDefault(
                                s.getCategoryCode(), s.getCategoryCode()),
                        Collectors.summingLong(s -> s.getAmount())
                ));
        Map<String, Long> sortedCategories = categoryMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                        (a, b) -> a, LinkedHashMap::new));
        var recentSpendings = spendings.stream()
                .sorted(Comparator.comparing(com.deveopsj.spending.entity.DailySpending::getSpendingDate,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(com.deveopsj.spending.entity.DailySpending::getId,
                                Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(s -> new DashboardSummary.RecentSpending(s.getSpendingDate(),
                        spendingCategoryMap.getOrDefault(s.getCategoryCode(), s.getCategoryCode()),
                        s.getAmount(), s.getMemo(), s.getCreateDate()))
                .toList();

        // 3. 전체 투자 달성률 계산
        double totalProgress = (totalTarget == 0) ? 0 : (totalActual / (double)totalTarget) * 100;
        double savingsRate = livingIncome == 0
                ? 0 : (totalActual / (double) livingIncome) * 100;
        long availableCash = Math.subtractExact(
                Math.subtractExact(livingIncome, totalSpend), totalActual);

        return DashboardSummary.builder()
                .totalInvestmentTarget(totalTarget)
                .totalInvestment(totalActual)
                .extraInvestment(extraInvestment)
                .valuationPrincipal(valuationPrincipal)
                .currentValuation(currentValuation)
                .valuationProfit(Math.addExact(Math.subtractExact(currentValuation,valuationPrincipal),sourceProfitAdjustment))
                .valuedPlanCount(valuedPlanCount)
                .totalIncome(totalIncome)
                .livingIncome(livingIncome)
                .dividendIncome(dividendIncome)
                .interestIncome(interestIncome)
                .investmentIncome(investmentIncome)
                .incomeSpendingDifference(Math.subtractExact(livingIncome, totalSpend))
                .salaryIncome(salaryIncome)
                .expectedSalary(expectedSalary)
                .expectedSalaryDate(expectedSalary > 0 ? end : null)
                .totalSpending(totalSpend)
                .futureSpending(futureSpendings.stream().mapToLong(s -> s.getAmount()).reduce(0L, Math::addExact))
                .futureSpendingCount(futureSpendings.size())
                .consumptionAmount(totalSpend - fixedExpenseAmount)
                .fixedExpenseAmount(fixedExpenseAmount)
                .availableCash(availableCash)
                .savingsRate(savingsRate)
                .spendingByCategory(sortedCategories)
                .recentSpendings(recentSpendings)
                .spendingCount(spendings.size())
                .investmentProgress(Math.max(0, Math.min(totalProgress, 100.0)))
                .planProgressList(planProgressList)
                .build();
    }
}
