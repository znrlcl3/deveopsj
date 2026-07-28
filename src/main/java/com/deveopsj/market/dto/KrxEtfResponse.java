package com.deveopsj.market.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KrxEtfResponse(
        @JsonProperty("OutBlock_1") List<KrxEtfItem> etfs) {
}
