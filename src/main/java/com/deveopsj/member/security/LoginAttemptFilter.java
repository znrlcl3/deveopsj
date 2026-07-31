package com.deveopsj.member.security;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LoginAttemptFilter extends OncePerRequestFilter {
    private final LoginAttemptService loginAttemptService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod())
                && "/member/login-proc".equals(request.getServletPath()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String loginId = request.getParameter("loginId");
        if (loginAttemptService.isBlocked(loginId, request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/member/login?blocked");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
