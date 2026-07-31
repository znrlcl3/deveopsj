package com.deveopsj.member.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class MemberJoinDtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void 올바른_회원가입_입력은_허용한다() {
        MemberJoinDto request = request("member_01", "password123!", "사용자");

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void 짧거나_허용되지_않은_회원가입_입력은_거부한다() {
        MemberJoinDto request = request("한", "short", " ");

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("loginId", "password", "name");
    }

    private MemberJoinDto request(String loginId, String password, String name) {
        MemberJoinDto request = new MemberJoinDto();
        request.setLoginId(loginId);
        request.setPassword(password);
        request.setName(name);
        return request;
    }
}
