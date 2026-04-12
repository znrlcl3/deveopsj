package com.deveopsj.dashboard.service;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    private final AssetPlanRepository assetPlanRepository;
    private final DailySpendingRepository dailySpendingRepository;

    public DashboardSummary getMonthlySummary(Long memberId) {
        LocalDate start = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = LocalDate.now().with(TemporalAdjusters.lastDayOfMonth());

        // 1. 이번 달 총 투자액 계산
        Long totalInv = assetPlanRepository.findByMemberMemberIdAndPlanDateBetween(memberId, start, end)
                .stream().mapToLong(plan -> plan.getAmount()).sum();

        // 2. 이번 달 지출 내역 및 카테고리별 합산
        var spendings = dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(memberId, start, end);
        
        Long totalSpend = spendings.stream().mapToLong(s -> s.getAmount()).sum();
        
        var categoryMap = spendings.stream()
                .collect(Collectors.groupingBy(
                        s -> s.getCategoryCode(), 
                        Collectors.summingLong(s -> s.getAmount())
                ));

        // 3. 투자 달성률 계산 (목표 2,050,000 기준)
        double progress = (totalInv / 2050000.0) * 100;

        DashboardSummary summary = DashboardSummary.builder()
                .totalInvestment(totalInv)
                .totalSpending(totalSpend)
                .spendingByCategory(categoryMap)
                .investmentProgress(Math.min(progress, 100.0))
                .build();

        return summary;
    }
}