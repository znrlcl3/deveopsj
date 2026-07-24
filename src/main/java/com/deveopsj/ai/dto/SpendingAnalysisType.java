package com.deveopsj.ai.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum SpendingAnalysisType {

    SAVING_OPPORTUNITIES(
            "절약 가능 지출 찾기",
            "반복되거나 상대적으로 과도한 지출을 근거와 함께 찾고 절감 후보 금액을 제안해라."),
    CATEGORY_REVIEW(
            "카테고리별 소비 평가",
            "카테고리별 금액과 비중을 평가하고 개선 우선순위를 제안해라."),
    MONTHLY_COMPARISON(
            "지난달과 비교",
            "선택한 달과 이전 달의 총액 및 카테고리 변화를 비교하고 주요 증감 원인을 설명해라."),
    NEXT_MONTH_PLAN(
            "다음 달 절약 계획",
            "선택한 달의 소비를 바탕으로 다음 달에 실행 가능한 절약 목표를 금액과 함께 제안해라.");

    private final String title;
    private final String instruction;
}
