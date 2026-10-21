package com.deveopsj.dashboard.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummary {
    private Long totalInvestmentTarget;
    private Long totalInvestment;
    private Long extraInvestment;
    private Long valuationPrincipal;
    private Long currentValuation;
    private Long valuationProfit;
    private int valuedPlanCount;
    private Long totalIncome;
    private Long totalSpending;
    private long consumptionAmount;
    private long fixedExpenseAmount;
    private Long availableCash;
    private Double savingsRate;
    private Map<String, Long> spendingByCategory; 
    private Double investmentProgress; 
    private List<PlanProgressDto> planProgressList;
    private List<RecentSpending> recentSpendings;
    private int spendingCount;

    public record RecentSpending(java.time.LocalDate spendingDate, String categoryName,
            Long amount, String memo, java.time.LocalDateTime createDate) {}

    @Builder @Getter
    public static class PlanProgressDto {
        private String goalTitle;
        private String assetType;
        private Long monthlyAmount;
        private Long actualAmount;
        private double progress;
    }
}
