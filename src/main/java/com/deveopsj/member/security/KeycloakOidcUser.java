package com.deveopsj.member.security;

import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

import com.deveopsj.member.entity.Member;

public class KeycloakOidcUser extends DefaultOidcUser implements MemberPrincipal {

    private final Member member;

    public KeycloakOidcUser(Collection<? extends GrantedAuthority> authorities,
            OidcIdToken idToken, OidcUserInfo userInfo, Member member) {
        super(authorities, idToken, userInfo, "preferred_username");
        this.member = member;
    }

    @Override
    public Member getMember() {
        return member;
    }

    @Override
    public boolean isExternal() {
        return true;
    }
}
