package com.deveopsj.spending.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SpendingUpdateRequest {

    @NotNull(message = "수정할 지출 내역이 필요합니다.")
    private Long id;

    @NotNull(message = "지출 날짜를 입력해 주세요.")
    @PastOrPresent(message = "지출 날짜는 미래일 수 없습니다.")
    private LocalDate date;

    @NotNull(message = "지출 금액을 입력해 주세요.")
    @Positive(message = "지출 금액은 0보다 커야 합니다.")
    private Long amount;

    @NotBlank(message = "지출 메모를 입력해 주세요.")
    @Size(max = 200, message = "지출 메모는 200자 이하여야 합니다.")
    private String memo;

    @NotBlank(message = "카테고리를 선택해 주세요.")
    @Size(max = 20, message = "카테고리는 20자 이하여야 합니다.")
    private String category;
}
