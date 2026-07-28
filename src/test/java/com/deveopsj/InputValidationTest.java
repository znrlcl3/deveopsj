package com.deveopsj;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.deveopsj.assetplan.dto.AssetPlanSaveRequest;
import com.deveopsj.assetplan.dto.AssetPlanUpdateRequest;
import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.dto.AssetValuationSaveRequest;
import com.deveopsj.assetplan.dto.GoalSaveRequest;
import com.deveopsj.assetplan.entity.Goal.GoalType;
import com.deveopsj.ai.dto.SpendingAnalysisRequest;
import com.deveopsj.ai.dto.SpendingAnalysisType;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.dto.SpendingUpdateRequest;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class InputValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void 목표는_이름과_양수금액이_필요하고_기간형은_종료일이_필요하다() {
        GoalSaveRequest request = new GoalSaveRequest();
        request.setTitle(" ");
        request.setTargetAmount(0L);
        request.setType(GoalType.TERM);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("title", "targetAmount", "endDateValid");
    }

    @Test
    void 자산계획은_필수선택과_양수금액을_검증한다() {
        AssetPlanSaveRequest request = new AssetPlanSaveRequest();
        request.setMonthlyAmount(-1L);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("goalId", "assetType", "monthlyAmount");
    }

    @Test
    void 자산계획수정은_ID와_필수선택_양수금액을_검증한다() {
        AssetPlanUpdateRequest request = new AssetPlanUpdateRequest();
        request.setAssetType(" ");
        request.setMonthlyAmount(0L);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("id", "goalId", "assetType", "monthlyAmount");
    }

    @Test
    void 적립은_미래날짜와_0원이_허용되지_않는다() {
        AssetSavingsSaveRequest request = new AssetSavingsSaveRequest();
        request.setAssetPlanId(1L);
        request.setAmount(0L);
        request.setDepositDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("amount", "depositDate");
    }

    @Test
    void 자산평가는_미래날짜와_음수금액이_허용되지_않는다() {
        AssetValuationSaveRequest request = new AssetValuationSaveRequest();
        request.setAssetPlanId(1L);
        request.setValuationAmount(-1L);
        request.setValuationDate(LocalDate.now().plusDays(1));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("valuationAmount", "valuationDate");
    }

    @Test
    void 주식거래는_필수종목정보와_양수수량을_검증한다() {
        AssetTradeSaveRequest request = new AssetTradeSaveRequest();
        request.setTradeDate(LocalDate.now().plusDays(1));
        request.setQuantity(java.math.BigDecimal.ZERO);
        request.setUnitPrice(java.math.BigDecimal.ZERO);
        request.setCurrency("KRW");
        request.setExchangeRate(java.math.BigDecimal.ONE);
        request.setFeeKrw(0L);
        request.setTaxKrw(0L);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("assetPlanId", "symbol", "assetName", "market", "assetClass",
                        "tradeType", "tradeDate", "quantity", "unitPrice");
    }

    @Test
    void 지출은_미래날짜_0원_공백메모가_허용되지_않는다() {
        SpendingSaveRequest request = new SpendingSaveRequest();
        request.setDate(LocalDate.now().plusDays(1));
        request.setAmount(0L);
        request.setMemo(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("date", "amount", "memo");
    }

    @Test
    void 지출수정은_ID와_카테고리가_필요하다() {
        SpendingUpdateRequest request = new SpendingUpdateRequest();
        request.setDate(LocalDate.now());
        request.setAmount(1_000L);
        request.setMemo("점심");
        request.setCategory(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("id", "category");
    }

    @Test
    void 미래월의_AI지출분석은_허용하지_않는다() {
        SpendingAnalysisRequest request = new SpendingAnalysisRequest();
        request.setMonth(java.time.YearMonth.now().plusMonths(1));
        request.setAnalysisType(SpendingAnalysisType.CATEGORY_REVIEW);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("monthValid");
    }
}
