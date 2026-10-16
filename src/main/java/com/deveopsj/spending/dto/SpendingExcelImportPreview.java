package com.deveopsj.spending.dto;
import java.io.Serializable;
import java.util.List;
public record SpendingExcelImportPreview(String token, Long memberId, String originalFilename,
        String month, List<SpendingExcelImportRow> rows) implements Serializable {}
