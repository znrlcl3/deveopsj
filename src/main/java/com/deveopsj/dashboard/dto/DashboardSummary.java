package com.deveopsj.dashboard.dto;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DashboardSummary {
    private Long totalInvestment;     // 이번 달 총 투자액 (목표: 205만)
    private Long totalSpending;       // 이번 달 총 지출액 (목표: 55만)
    private Map<String, Long> spendingByCategory; // 카테고리별 지출 분포
    private Double investmentProgress; // 투자 달성률 (%)
    private String aiBriefing;        // (추후) Gemini가 채워줄 한 줄 평
}