package com.deveopsj.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccountDeactivationDto {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    @Size(max = 72, message = "현재 비밀번호는 72자 이하여야 합니다.")
    private String currentPassword;

    @NotBlank(message = "확인 문구를 입력해 주세요.")
    @Size(max = 20, message = "확인 문구를 확인해 주세요.")
    private String confirmation;
}
