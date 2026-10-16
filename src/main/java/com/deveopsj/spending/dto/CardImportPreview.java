package com.deveopsj.spending.dto;

import java.io.Serializable;
import java.util.List;

public record CardImportPreview(
        String token,
        CardCompany cardCompany,
        String originalFilename,
        List<CardSpendingImportRow> rows,
        String notice,
        Long memberId) implements Serializable {

    public CardImportPreview(String token, CardCompany cardCompany, String originalFilename,
            List<CardSpendingImportRow> rows, String notice) {
        this(token, cardCompany, originalFilename, rows, notice, null);
    }

    public CardImportPreview(String token, CardCompany cardCompany, String originalFilename,
            List<CardSpendingImportRow> rows) {
        this(token, cardCompany, originalFilename, rows, null);
    }
}
