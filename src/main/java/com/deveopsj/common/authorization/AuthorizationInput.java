package com.deveopsj.common.authorization;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AuthorizationInput(
        Subject subject,
        String action,
        Resource resource) {

    public record Subject(
            @JsonProperty("member_id") Long memberId,
            String role) {
    }

    public record Resource(
            String type,
            Long id,
            @JsonProperty("owner_id") Long ownerId) {
    }
}
