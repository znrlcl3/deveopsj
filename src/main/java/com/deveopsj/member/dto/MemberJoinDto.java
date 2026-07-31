package com.deveopsj.member.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class MemberJoinDto {

    @NotBlank(message = "아이디를 입력해 주세요.")
    @Size(min = 4, max = 50, message = "아이디는 4자 이상 50자 이하로 입력해 주세요.")
    @Pattern(regexp = "^[A-Za-z0-9._-]+$",
            message = "아이디는 영문, 숫자, 마침표, 밑줄, 하이픈만 사용할 수 있습니다.")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하로 입력해 주세요.")
    private String password;

    @NotBlank(message = "이름을 입력해 주세요.")
    @Size(max = 50, message = "이름은 50자 이하로 입력해 주세요.")
    private String name;
}
