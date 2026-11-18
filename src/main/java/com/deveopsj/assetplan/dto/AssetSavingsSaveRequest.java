package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import com.deveopsj.assetplan.entity.AssetSavings.DepositType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetSavingsSaveRequest {

    private Long assetPlanId;

    private Long goalId;

    @NotNull(message = "납입 유형을 선택해 주세요.")
    private DepositType depositType;

    @NotNull(message = "적립 금액을 입력해 주세요.")
    @jakarta.validation.constraints.Min(-999999999999999L)
    @jakarta.validation.constraints.Max(999999999999999L)
    private Long amount;

    @AssertTrue(message = "금액은 0일 수 없습니다. 인출은 음수로 입력하세요.")
    public boolean isAmountNonZero() { return amount == null || amount != 0; }

    @NotNull(message = "적립 날짜를 입력해 주세요.")
    @PastOrPresent(message = "적립 날짜는 미래일 수 없습니다.")
    private LocalDate depositDate;

    @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
    private String memo;

    @AssertTrue(message = "계획 납입은 플랜을, 기타 추가 납입은 목표를 선택해 주세요.")
    public boolean isTargetValid() {
        if (depositType == null) {
            return true;
        }
        return depositType == DepositType.PLAN
                ? assetPlanId != null
                : goalId != null && assetPlanId == null;
    }
}
