package com.deveopsj.spending.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RecurringExpenseRequest {

    private Long id;

    @NotBlank(message = "고정지출 이름을 입력해 주세요.")
    @Size(max = 100, message = "이름은 100자 이하로 입력해 주세요.")
    private String name;

    @NotBlank(message = "카테고리를 선택해 주세요.")
    @Size(max = 20)
    private String category;

    @NotNull(message = "금액을 입력해 주세요.")
    @Positive(message = "금액은 0보다 커야 합니다.")
    private Long amount;

    @NotNull(message = "결제일을 입력해 주세요.")
    @Min(value = 1, message = "결제일은 1일부터 31일까지 입력해 주세요.")
    @Max(value = 31, message = "결제일은 1일부터 31일까지 입력해 주세요.")
    private Integer paymentDay;

    @NotNull(message = "시작일을 입력해 주세요.")
    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 200, message = "메모는 200자 이하로 입력해 주세요.")
    private String memo;

    @AssertTrue(message = "종료일은 시작일보다 빠를 수 없습니다.")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }
}
