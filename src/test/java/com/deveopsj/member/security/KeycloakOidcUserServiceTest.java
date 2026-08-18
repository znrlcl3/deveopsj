package com.deveopsj.member.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;

class KeycloakOidcUserServiceTest {

    @Test
    void 신규_Keycloak_사용자를_로컬_USER로_생성한다() {
        MemberRepository repository = mock(MemberRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = delegate(oidcUser());
        when(repository.findByOidcIssuerAndOidcSubject("https://sso.example/realms/deveopsj", "subject-7"))
                .thenReturn(Optional.empty());
        when(repository.findByLoginId("keycloak-user")).thenReturn(Optional.empty());
        when(encoder.encode(any())).thenReturn("encoded-random-password");
        when(repository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OidcUser result = service(repository, encoder, delegate).loadUser(mock(OidcUserRequest.class));

        Member member = ((MemberPrincipal) result).getMember();
        assertThat(member.getLoginId()).isEqualTo("keycloak-user");
        assertThat(member.getName()).isEqualTo("Keycloak User");
        assertThat(member.getRole()).isEqualTo("USER");
        assertThat(member.getOidcSubject()).isEqualTo("subject-7");
        assertThat(result.getAuthorities()).extracting("authority").containsExactly("ROLE_USER");
    }

    @Test
    void issuer와_subject로_연결된_회원을_재사용한다() {
        MemberRepository repository = mock(MemberRepository.class);
        Member existing = member("linked-user");
        when(repository.findByOidcIssuerAndOidcSubject("https://sso.example/realms/deveopsj", "subject-7"))
                .thenReturn(Optional.of(existing));

        OidcUser result = service(repository, mock(PasswordEncoder.class), delegate(oidcUser()))
                .loadUser(mock(OidcUserRequest.class));

        assertThat(((MemberPrincipal) result).getMember()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    @Test
    void 같은_아이디의_로컬계정을_자동연결하지_않는다() {
        MemberRepository repository = mock(MemberRepository.class);
        when(repository.findByOidcIssuerAndOidcSubject(any(), any())).thenReturn(Optional.empty());
        when(repository.findByLoginId("keycloak-user")).thenReturn(Optional.of(member("keycloak-user")));

        assertThatThrownBy(() -> service(repository, mock(PasswordEncoder.class), delegate(oidcUser()))
                .loadUser(mock(OidcUserRequest.class)))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(error -> assertThat(((OAuth2AuthenticationException) error).getError().getErrorCode())
                        .isEqualTo("oidc_account_conflict"));
        verify(repository, never()).save(any());
    }

    private KeycloakOidcUserService service(MemberRepository repository, PasswordEncoder encoder,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        return new KeycloakOidcUserService(repository, encoder, delegate);
    }

    @SuppressWarnings("unchecked")
    private OAuth2UserService<OidcUserRequest, OidcUser> delegate(OidcUser user) {
        OAuth2UserService<OidcUserRequest, OidcUser> delegate = mock(OAuth2UserService.class);
        when(delegate.loadUser(any())).thenReturn(user);
        return delegate;
    }

    private OidcUser oidcUser() {
        Map<String, Object> claims = Map.of(
                "iss", "https://sso.example/realms/deveopsj",
                "sub", "subject-7",
                "preferred_username", "keycloak-user",
                "name", "Keycloak User");
        OidcIdToken idToken = new OidcIdToken(
                "token", Instant.now(), Instant.now().plusSeconds(300), claims);
        return new DefaultOidcUser(List.of(), idToken, new OidcUserInfo(claims), "preferred_username");
    }

    private Member member(String loginId) {
        Member member = new Member();
        member.setLoginId(loginId);
        member.setRole("USER");
        return member;
    }
}
