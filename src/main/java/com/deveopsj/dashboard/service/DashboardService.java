package com.deveopsj.dashboard.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
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

import com.deveopsj.common.service.MasterCodeService;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetPlanRepository assetPlanRepository;
    private final AssetSavingsRepository assetSavingsRepository;
    private final DailySpendingRepository dailySpendingRepository;
    private final GoalRepository goalRepository;
    private final MasterCodeService masterCodeService;

    public DashboardSummary getMonthlySummary(Long memberId) {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        // 1. 자산 플랜 및 이행률 계산
        List<AssetPlan> plans = assetPlanRepository.findByMemberMemberId(memberId);
        List<DashboardSummary.PlanProgressDto> planProgressList = new ArrayList<>();
        
        long totalTarget = 0;
        long totalActual = 0;
        
        // 자산 유형 코드 매핑용 맵
        Map<String, String> assetTypeMap = masterCodeService.getAllActiveCodesGrouped().get("ASSET_TYPE")
            .stream().collect(Collectors.toMap(c -> c.getCodeId(), c -> c.getCodeName()));

        for (AssetPlan plan : plans) {
            totalTarget += plan.getMonthlyAmount();
            Long savings = assetSavingsRepository.getTotalSavingsByPlanIdAndDepositDateBetween(
                    plan.getId(), start, end);
            long actual = (savings != null ? savings : 0);
            totalActual += actual;
            
            double progress = (plan.getMonthlyAmount() == 0) ? 0 : (actual / (double)plan.getMonthlyAmount()) * 100;
            
            planProgressList.add(DashboardSummary.PlanProgressDto.builder()
                .goalTitle(plan.getGoal().getTitle())
                .assetType(assetTypeMap.getOrDefault(plan.getAssetType(), plan.getAssetType()))
                .monthlyAmount(plan.getMonthlyAmount())
                .actualAmount(actual)
                .progress(Math.min(progress, 100.0))
                .build());
        }

        // 2. 이번 달 지출 내역 계산
        var spendings = dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(memberId, start, end);
        Long totalSpend = spendings.stream().mapToLong(s -> s.getAmount()).sum();
        
        var categoryMap = spendings.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCategoryCode(), 
                        Collectors.summingLong(s -> s.getAmount())
                ));

        // 3. 전체 투자 달성률 계산
        double totalProgress = (totalTarget == 0) ? 0 : (totalActual / (double)totalTarget) * 100;

        return DashboardSummary.builder()
                .totalInvestment(totalActual)
                .totalSpending(totalSpend)
                .spendingByCategory(categoryMap)
                .investmentProgress(Math.min(totalProgress, 100.0))
                .planProgressList(planProgressList)
                .build();
    }
}
