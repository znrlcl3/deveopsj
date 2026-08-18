package com.deveopsj.member.security;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;

@Service
public class KeycloakOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private static final OAuth2Error ACCOUNT_CONFLICT = new OAuth2Error(
            "oidc_account_conflict", "같은 아이디의 로컬 계정이 이미 존재합니다.", null);

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

    public KeycloakOidcUserService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this(memberRepository, passwordEncoder, new OidcUserService());
    }

    KeycloakOidcUserService(MemberRepository memberRepository, PasswordEncoder passwordEncoder,
            OAuth2UserService<OidcUserRequest, OidcUser> delegate) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
        this.delegate = delegate;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = delegate.loadUser(userRequest);
        String issuer = oidcUser.getIssuer() == null ? null : oidcUser.getIssuer().toString();
        String subject = oidcUser.getSubject();
        if (!StringUtils.hasText(issuer) || !StringUtils.hasText(subject)) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "invalid_oidc_identity", "OIDC issuer 또는 subject가 없습니다.", null));
        }

        Member member = memberRepository.findByOidcIssuerAndOidcSubject(issuer, subject)
                .orElseGet(() -> provisionMember(oidcUser, issuer, subject));
        if (member.getDisableDate() != null) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "disabled_member", "비활성화된 계정입니다.", null));
        }

        return new KeycloakOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_" + member.getRole())),
                oidcUser.getIdToken(), oidcUser.getUserInfo(), member);
    }

    private Member provisionMember(OidcUser oidcUser, String issuer, String subject) {
        String loginId = requiredClaim(oidcUser, "preferred_username", 50);
        if (memberRepository.findByLoginId(loginId).isPresent()) {
            throw new OAuth2AuthenticationException(ACCOUNT_CONFLICT);
        }

        Member member = new Member();
        member.setLoginId(loginId);
        member.setName(displayName(oidcUser, loginId));
        member.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
        member.setRole("USER");
        member.setOidcIssuer(issuer);
        member.setOidcSubject(subject);
        return memberRepository.save(member);
    }

    private String displayName(OidcUser oidcUser, String fallback) {
        String name = oidcUser.getFullName();
        if (!StringUtils.hasText(name)) {
            name = fallback;
        }
        return name.trim().substring(0, Math.min(name.trim().length(), 50));
    }

    private String requiredClaim(OidcUser oidcUser, String claim, int maxLength) {
        String value = oidcUser.getClaimAsString(claim);
        if (!StringUtils.hasText(value) || value.trim().length() > maxLength) {
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    "invalid_oidc_claim", claim + " claim이 없거나 너무 깁니다.", null));
        }
        return value.trim();
    }
}
