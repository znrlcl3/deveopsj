package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import com.deveopsj.assetplan.entity.Goal.GoalType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GoalSaveRequest {

    @NotBlank(message = "목표명을 입력해 주세요.")
    @Size(max = 100, message = "목표명은 100자 이하여야 합니다.")
    private String title;

    @NotNull(message = "목표 금액을 입력해 주세요.")
    @Positive(message = "목표 금액은 0보다 커야 합니다.")
    private Long targetAmount;

    @NotNull(message = "목표 유형을 선택해 주세요.")
    private GoalType type;

    @FutureOrPresent(message = "종료일은 오늘 이후여야 합니다.")
    private LocalDate endDate;

    @AssertTrue(message = "기간 제한 목표는 종료일이 필요합니다.")
    public boolean isEndDateValid() {
        return type != GoalType.TERM || endDate != null;
    }
}
