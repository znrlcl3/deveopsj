package com.deveopsj.income.dto;

import java.time.LocalDate;

import com.deveopsj.income.entity.Income.IncomeType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IncomeSaveRequest {

    @NotNull(message = "수입 날짜를 입력해 주세요.")
    @PastOrPresent(message = "수입 날짜는 미래일 수 없습니다.")
    private LocalDate incomeDate;

    @NotNull(message = "수입 유형을 선택해 주세요.")
    private IncomeType incomeType;

    @NotNull(message = "수입 금액을 입력해 주세요.")
    @Positive(message = "수입 금액은 0보다 커야 합니다.")
    private Long amount;

    @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
    private String memo;
}
