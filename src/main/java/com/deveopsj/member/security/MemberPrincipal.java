package com.deveopsj.member.security;

import com.deveopsj.member.entity.Member;

public interface MemberPrincipal {

    Member getMember();

    default String getLoginId() {
        return getMember().getLoginId();
    }

    default boolean isExternal() {
        return false;
    }
}
