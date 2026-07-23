package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetSavingsSaveRequest {

    @NotNull(message = "대상 플랜을 선택해 주세요.")
    private Long assetPlanId;

    @NotNull(message = "적립 금액을 입력해 주세요.")
    @Positive(message = "적립 금액은 0보다 커야 합니다.")
    private Long amount;

    @NotNull(message = "적립 날짜를 입력해 주세요.")
    @PastOrPresent(message = "적립 날짜는 미래일 수 없습니다.")
    private LocalDate depositDate;

    @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
    private String memo;
}
