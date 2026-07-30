package com.deveopsj.income.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomeUpdateRequest extends IncomeSaveRequest {

    @NotNull(message = "수정할 수입 내역을 선택해 주세요.")
    private Long id;
}
