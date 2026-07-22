package com.deveopsj.dashboard.dto;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummary {
    private Long totalInvestment;    
    private Long totalSpending;      
    private Map<String, Long> spendingByCategory; 
    private Double investmentProgress; 
    private String aiBriefing;      
    private List<PlanProgressDto> planProgressList;

    @Builder @Getter
    public static class PlanProgressDto {
        private String goalTitle;
        private String assetType;
        private Long monthlyAmount;
        private Long actualAmount;
        private double progress;
    }
}