package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetValuationSaveRequest {

    @NotNull(message = "대상 플랜을 선택해 주세요.")
    private Long assetPlanId;

    @NotNull(message = "평가금액을 입력해 주세요.")
    @PositiveOrZero(message = "평가금액은 0 이상이어야 합니다.")
    private Long valuationAmount;

    @NotNull(message = "평가 기준일을 입력해 주세요.")
    @PastOrPresent(message = "평가 기준일은 미래일 수 없습니다.")
    private LocalDate valuationDate;
}
