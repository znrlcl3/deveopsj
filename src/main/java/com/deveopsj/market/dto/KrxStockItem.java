package com.deveopsj.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KrxStockItem(
        @JsonProperty("ISU_CD") String standardCode,
        @JsonProperty("ISU_SRT_CD") String shortCode,
        @JsonProperty("ISU_NM") String name,
        @JsonProperty("ISU_ABBRV") String abbreviatedName,
        @JsonProperty("ISU_ENG_NM") String englishName,
        @JsonProperty("LIST_DD") String listingDate,
        @JsonProperty("MKT_TP_NM") String market,
        @JsonProperty("SECUGRP_NM") String securityGroup,
        @JsonProperty("KIND_STKCERT_TP_NM") String stockType,
        @JsonProperty("LIST_SHRS") String listedShares) {
}
