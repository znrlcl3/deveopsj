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
public class AssetSavingsUpdateRequest {

    @NotNull(message = "수정할 납입 내역을 선택해 주세요.")
    private Long id;

    private Long assetPlanId;

    private Long goalId;

    @NotNull(message = "납입 유형을 선택해 주세요.")
    private DepositType depositType;

    @NotNull(message = "납입 금액을 입력해 주세요.")
    @Positive(message = "납입 금액은 0보다 커야 합니다.")
    private Long amount;

    @NotNull(message = "납입 날짜를 입력해 주세요.")
    @PastOrPresent(message = "납입 날짜는 미래일 수 없습니다.")
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
