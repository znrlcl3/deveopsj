package com.deveopsj.market.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KrxEtfItem(
        @JsonProperty("ISU_CD") String code,
        @JsonProperty("ISU_NM") String name,
        @JsonProperty("LIST_SHRS") String listedShares) {

    public KrxStockItem toStockItem() {
        return new KrxStockItem(
                null, code, name, name, null, null,
                "ETF", "ETF", "ETF", listedShares);
    }
}
