package com.deveopsj.assetplan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetPlanUpdateRequest {

    @NotNull(message = "수정할 자산 플랜을 선택해 주세요.")
    private Long id;

    @NotNull(message = "관련 목표를 선택해 주세요.")
    private Long goalId;

    @NotBlank(message = "플랜 이름을 입력해 주세요.")
    @Size(max = 100, message = "플랜 이름은 100자 이하여야 합니다.")
    private String planName;

    @NotBlank(message = "자산 유형을 선택해 주세요.")
    @Size(max = 20, message = "자산 유형은 20자 이하여야 합니다.")
    private String assetType;

    @NotNull(message = "월 투자 금액을 입력해 주세요.")
    @Positive(message = "월 투자 금액은 0보다 커야 합니다.")
    private Long monthlyAmount;

    @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
    private String memo;
}
