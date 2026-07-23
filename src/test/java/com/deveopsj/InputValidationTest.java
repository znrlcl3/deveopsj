package com.deveopsj;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.deveopsj.assetplan.dto.AssetPlanSaveRequest;
import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.dto.GoalSaveRequest;
import com.deveopsj.assetplan.entity.Goal.GoalType;
import com.deveopsj.spending.dto.SpendingSaveRequest;

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
    void 지출은_미래날짜_0원_공백메모가_허용되지_않는다() {
        SpendingSaveRequest request = new SpendingSaveRequest();
        request.setDate(LocalDate.now().plusDays(1));
        request.setAmount(0L);
        request.setMemo(" ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("date", "amount", "memo");
    }
}
