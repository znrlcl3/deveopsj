package com.deveopsj.dashboard.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetPlanRepository assetPlanRepository;
    private final AssetSavingsRepository assetSavingsRepository;
    private final DailySpendingRepository dailySpendingRepository;
    private final GoalRepository goalRepository;

    public DashboardSummary getMonthlySummary(Long memberId) {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        // 1. 이번 달 자산 플랜 조회 및 실적 계산
        List<AssetPlan> plans = assetPlanRepository.findByMemberMemberId(memberId);
        
        long totalTarget = 0;
        long totalActual = 0;
        
        for (AssetPlan plan : plans) {
            totalTarget += plan.getMonthlyAmount();
            Long savings = assetSavingsRepository.getTotalSavingsByPlanId(plan.getId());
            totalActual += (savings != null ? savings : 0);
        }

        // 2. 이번 달 지출 내역 계산
        var spendings = dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(memberId, start, end);
        Long totalSpend = spendings.stream().mapToLong(s -> s.getAmount()).sum();
        
        var categoryMap = spendings.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCategoryCode(), 
                        Collectors.summingLong(s -> s.getAmount())
                ));

        // 3. 투자 달성률 계산 (전체 플랜 대비 전체 적립액)
        double progress = (totalTarget == 0) ? 0 : (totalActual / (double)totalTarget) * 100;

        return DashboardSummary.builder()
                .totalInvestment(totalActual)
                .totalSpending(totalSpend)
                .spendingByCategory(categoryMap)
                .investmentProgress(Math.min(progress, 100.0))
                .build();
    }
}