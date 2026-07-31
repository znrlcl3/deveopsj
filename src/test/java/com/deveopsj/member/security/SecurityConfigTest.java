package com.deveopsj.member.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;

class SecurityConfigTest {

    @Test
    void 대리로그인필터가_필수설정과_함께_초기화된다() {
        SecurityConfig securityConfig = new SecurityConfig();
        SwitchUserFilter filter = securityConfig.switchUserFilter(
                mock(UserDetailsService.class));

        assertThatCode(filter::afterPropertiesSet).doesNotThrowAnyException();
    }
}
