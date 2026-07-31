package com.deveopsj.member.dto;

import java.time.LocalDateTime;

import com.deveopsj.member.entity.Member;

public record AdminMemberView(
        Long memberId,
        String loginId,
        String name,
        String role,
        LocalDateTime createDate,
        LocalDateTime disableDate) {

    public static AdminMemberView from(Member member) {
        return new AdminMemberView(
                member.getMemberId(),
                member.getLoginId(),
                member.getName(),
                member.getRole(),
                member.getCreateDate(),
                member.getDisableDate());
    }

    public boolean active() {
        return disableDate == null;
    }
}
