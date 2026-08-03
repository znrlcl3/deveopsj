package com.deveopsj.assetplan.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.deveopsj.assetplan.entity.AssetTrade.TradeType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AssetTradeSaveRequest {

    @NotNull(message = "대상 플랜을 선택해 주세요.")
    private Long assetPlanId;

    @NotNull(message = "종목을 검색해서 선택해 주세요.")
    private Long investmentAssetId;

    @NotNull(message = "거래 유형을 선택해 주세요.")
    private TradeType tradeType;

    @NotNull(message = "거래일을 입력해 주세요.")
    @PastOrPresent(message = "거래일은 미래일 수 없습니다.")
    private LocalDate tradeDate;

    @NotNull(message = "수량을 입력해 주세요.")
    @DecimalMin(value = "0.00000001", message = "수량은 0보다 커야 합니다.")
    @Digits(integer = 11, fraction = 8, message = "수량의 자릿수를 확인해 주세요.")
    private BigDecimal quantity;

    @DecimalMin(value = "0.0001", message = "총 매매금액은 0보다 커야 합니다.")
    @Digits(integer = 15, fraction = 4, message = "총 매매금액의 자릿수를 확인해 주세요.")
    private BigDecimal tradeAmount;

    @DecimalMin(value = "0.0001", message = "체결단가는 0보다 커야 합니다.")
    @Digits(integer = 15, fraction = 4, message = "체결단가의 자릿수를 확인해 주세요.")
    private BigDecimal unitPrice;

    private String calculationBasis;

    @NotNull(message = "환율을 입력해 주세요.")
    @DecimalMin(value = "0.000001", message = "환율은 0보다 커야 합니다.")
    @Digits(integer = 13, fraction = 6, message = "환율의 자릿수를 확인해 주세요.")
    private BigDecimal exchangeRate;

    @NotNull(message = "수수료를 입력해 주세요.")
    @PositiveOrZero(message = "수수료는 0 이상이어야 합니다.")
    private Long feeKrw;

    @NotNull(message = "세금을 입력해 주세요.")
    @PositiveOrZero(message = "세금은 0 이상이어야 합니다.")
    private Long taxKrw;

    @Size(max = 200, message = "메모는 200자 이하여야 합니다.")
    private String memo;

    @AssertTrue(message = "총 매매금액 또는 체결단가를 입력해 주세요.")
    public boolean isTradePricePresent() {
        return tradeAmount != null || unitPrice != null;
    }
}
