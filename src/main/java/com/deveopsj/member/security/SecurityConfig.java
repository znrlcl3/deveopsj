package com.deveopsj.member.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.switchuser.SwitchUserFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.web.authentication.logout.SimpleUrlLogoutSuccessHandler;

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
            LoginAttemptService loginAttemptService,
            SwitchUserFilter switchUserFilter,
            KeycloakOidcUserService keycloakOidcUserService,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepositoryProvider,
            @Value("${app.security.oidc.enabled:false}") boolean oidcEnabled) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/error", "/actuator/health", "/actuator/health/**",
                        "/privacy",
                        "/member/login", "/member/join", "/member/join-proc",
                        "/oauth2/**", "/login/oauth2/**",
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
                    if (hasCause(exception, DisabledException.class)) {
                        response.sendRedirect(request.getContextPath() + "/member/login?disabled");
                    } else {
                        loginAttemptService.loginFailed(
                                request.getParameter("loginId"), request.getRemoteAddr());
                        response.sendRedirect(request.getContextPath() + "/member/login?error");
                    }
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
            .exceptionHandling(exceptions ->
                    exceptions.accessDeniedPage("/access-denied"))
            .sessionManagement(session ->
                    session.invalidSessionUrl("/member/login?expired"))
            .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(switchUserFilter, AuthorizationFilter.class);

        if (oidcEnabled) {
            ClientRegistrationRepository registrations = clientRegistrationRepositoryProvider
                    .getIfAvailable(() -> {
                        throw new IllegalStateException(
                                "OIDC가 활성화됐지만 Keycloak client registration이 없습니다.");
                    });
            OidcClientInitiatedLogoutSuccessHandler oidcLogout =
                    new OidcClientInitiatedLogoutSuccessHandler(registrations);
            oidcLogout.setPostLogoutRedirectUri("{baseUrl}/member/login?logout");
            SimpleUrlLogoutSuccessHandler localLogout = new SimpleUrlLogoutSuccessHandler();
            localLogout.setDefaultTargetUrl("/member/login?logout");
            http.oauth2Login(oauth2 -> oauth2
                    .loginPage("/member/login")
                    .userInfoEndpoint(userInfo ->
                            userInfo.oidcUserService(keycloakOidcUserService))
                    .defaultSuccessUrl("/dashboard/view", true)
                    .failureUrl("/member/login?ssoError"));
            http.logout(logout -> logout.logoutSuccessHandler((request, response, authentication) -> {
                if (authentication != null
                        && authentication.getPrincipal() instanceof KeycloakOidcUser) {
                    oidcLogout.onLogoutSuccess(request, response, authentication);
                } else {
                    localLogout.onLogoutSuccess(request, response, authentication);
                }
            }));
        }

        return http.build();
    }

    @Bean
    public SwitchUserFilter switchUserFilter(UserDetailsService userDetailsService) {
        SwitchUserFilter filter = new SwitchUserFilter();
        filter.setUserDetailsService(userDetailsService);
        filter.setUsernameParameter("loginId");
        filter.setSwitchUserUrl("/admin/members/impersonate");
        filter.setExitUserUrl("/impersonation/exit");
        filter.setTargetUrl("/dashboard/view");
        filter.setSwitchFailureUrl("/admin/members?impersonationError");
        filter.setSecurityContextRepository(new HttpSessionSecurityContextRepository());
        filter.setSwitchUserMatcher(request ->
                "POST".equals(request.getMethod())
                        && "/admin/members/impersonate".equals(request.getServletPath())
                        && hasAuthority("ROLE_ADMIN")
                        && !hasAuthority(SwitchUserFilter.ROLE_PREVIOUS_ADMINISTRATOR));
        filter.setExitUserMatcher(request ->
                "POST".equals(request.getMethod())
                        && "/impersonation/exit".equals(request.getServletPath())
                        && hasAuthority(SwitchUserFilter.ROLE_PREVIOUS_ADMINISTRATOR));
        filter.setUserDetailsChecker(user -> {
            if (!user.isEnabled()) {
                throw new DisabledException("비활성화된 계정입니다.");
            }
            if (user.getAuthorities().stream()
                    .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()))) {
                throw new BadCredentialsException("관리자 계정으로는 전환할 수 없습니다.");
            }
        });
        return filter;
    }

    private boolean hasAuthority(String expectedAuthority) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> expectedAuthority.equals(authority.getAuthority()));
    }

    private boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        Throwable current = throwable;
        while (current != null) {
            if (type.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
