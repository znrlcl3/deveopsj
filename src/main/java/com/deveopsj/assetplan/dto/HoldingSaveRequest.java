package com.deveopsj.assetplan.dto;

import java.math.BigDecimal;
import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class HoldingSaveRequest {
    @NotNull(message = "플랜을 선택해 주세요.")
    private Long assetPlanId;
    @NotNull(message = "검색 결과에서 종목을 선택해 주세요.")
    private Long investmentAssetId;
    @NotNull @DecimalMin("0.00000001") @Digits(integer = 11, fraction = 8)
    private BigDecimal quantity;
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4)
    private BigDecimal averagePrice;
    @DecimalMin("0.000001") @Digits(integer = 13, fraction = 6)
    private BigDecimal exchangeRate;
}
