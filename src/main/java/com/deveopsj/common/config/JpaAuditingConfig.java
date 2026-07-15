package com.deveopsj.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import com.deveopsj.member.security.CustomUserDetails;

import java.util.Optional;

@Configuration
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            // 인증되지 않았거나 익명 사용자인 경우 'system'으로 처리
            if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
                return Optional.of("system");
            }
            
            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            return Optional.of(userDetails.getMember().getLoginId());
        };
    }
}
