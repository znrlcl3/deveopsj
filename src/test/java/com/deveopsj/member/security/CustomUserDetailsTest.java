package com.deveopsj.member.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import com.deveopsj.member.entity.Member;

class CustomUserDetailsTest {

    @Test
    void 활성_회원만_로그인할_수_있다() {
        assertThat(new CustomUserDetails(member(null)).isEnabled()).isTrue();
        assertThat(new CustomUserDetails(member(LocalDateTime.now())).isEnabled()).isFalse();
    }

    private Member member(LocalDateTime disableDate) {
        Member member = new Member();
        member.setLoginId("member");
        member.setPassword("encoded-password");
        member.setRole("USER");
        member.setDisableDate(disableDate);
        return member;
    }
}
