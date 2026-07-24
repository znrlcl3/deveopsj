package com.deveopsj.ai.dto;

import java.time.YearMonth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpendingAnalysisRequest {

    @NotNull(message = "분석할 월을 선택해 주세요.")
    private YearMonth month;

    @NotNull(message = "분석 유형을 선택해 주세요.")
    private SpendingAnalysisType analysisType;

    @AssertTrue(message = "미래 월은 분석할 수 없습니다.")
    public boolean isMonthValid() {
        return month == null || !month.isAfter(YearMonth.now());
    }
}
