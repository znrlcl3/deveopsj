package com.deveopsj.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordChangeDto {

    @NotBlank(message = "현재 비밀번호를 입력해 주세요.")
    @Size(max = 72, message = "현재 비밀번호는 72자 이하여야 합니다.")
    private String currentPassword;

    @NotBlank(message = "새 비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "새 비밀번호는 8자 이상 72자 이하여야 합니다.")
    private String newPassword;

    @NotBlank(message = "새 비밀번호 확인을 입력해 주세요.")
    @Size(max = 72, message = "새 비밀번호 확인은 72자 이하여야 합니다.")
    private String confirmPassword;
}
