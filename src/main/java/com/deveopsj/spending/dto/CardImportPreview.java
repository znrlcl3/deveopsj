package com.deveopsj.spending.dto;

import java.io.Serializable;
import java.util.List;

public record CardImportPreview(
        String token,
        CardCompany cardCompany,
        String originalFilename,
        List<CardSpendingImportRow> rows) implements Serializable {
}
