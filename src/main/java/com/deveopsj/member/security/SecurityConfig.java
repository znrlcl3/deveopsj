package com.deveopsj.member.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            LoginAttemptFilter loginAttemptFilter,
            LoginAttemptService loginAttemptService) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error", "/actuator/health", "/actuator/health/**",
                        "/member/login", "/member/join", "/member/join-proc",
                        "/css/**", "/js/**", "/images/**").permitAll()
                .requestMatchers("/krx/**").hasRole("ADMIN")
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/member/login")
                .loginProcessingUrl("/member/login-proc")
                .defaultSuccessUrl("/dashboard/view", true)
                .usernameParameter("loginId")
                .passwordParameter("password")
                .successHandler((request, response, authentication) -> {
                    loginAttemptService.loginSucceeded(
                            request.getParameter("loginId"), request.getRemoteAddr());
                    response.sendRedirect(request.getContextPath() + "/dashboard/view");
                })
                .failureHandler((request, response, exception) -> {
                    loginAttemptService.loginFailed(
                            request.getParameter("loginId"), request.getRemoteAddr());
                    response.sendRedirect(request.getContextPath() + "/member/login?error");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/member/logout")
                .logoutSuccessUrl("/member/login?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
