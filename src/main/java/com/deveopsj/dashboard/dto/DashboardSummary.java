package com.deveopsj.dashboard.dto;

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
}