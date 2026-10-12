package com.deveopsj.spending.dto;

public enum CardCompany {
    SHINHAN("신한카드"),
    SAMSUNG("삼성카드"),
    KAKAO_BANK("카카오뱅크");

    private final String label;

    CardCompany(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
