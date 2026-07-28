package com.deveopsj.market.dto;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KrxMarket {
    KOSPI("유가증권(KOSPI)", "sto/stk_isu_base_info"),
    KOSDAQ("코스닥(KOSDAQ)", "sto/ksq_isu_base_info"),
    ETF("ETF", "etp/etf_bydd_trd"),
    ALL("국내 전체(KOSPI·KOSDAQ·ETF)", null),
    US("미국주식(NASDAQ·NYSE 등)", null);

    private final String displayName;
    private final String endpoint;
}
