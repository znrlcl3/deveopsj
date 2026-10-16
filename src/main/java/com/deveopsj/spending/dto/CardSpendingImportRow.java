package com.deveopsj.spending.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;

public record CardSpendingImportRow(
        int sourceRow,
        LocalDate spendingDate,
        String merchantName,
        long amount,
        String transactionKey,
        String errorMessage,
        String sourceSheet,
        List<SpendingDuplicateMatch> matches,
        boolean duplicateInFile,
        boolean alreadyImported) implements Serializable {

    public CardSpendingImportRow(int sourceRow, LocalDate spendingDate, String merchantName,
            long amount, String transactionKey, String errorMessage, String sourceSheet) {
        this(sourceRow, spendingDate, merchantName, amount, transactionKey, errorMessage, sourceSheet,
                List.of(), false, false);
    }

    public CardSpendingImportRow(int sourceRow, LocalDate spendingDate, String merchantName,
            long amount, String transactionKey, String errorMessage) {
        this(sourceRow, spendingDate, merchantName, amount, transactionKey, errorMessage, null);
    }

    public String sourceLabel() {
        return sourceSheet == null ? Integer.toString(sourceRow) : sourceSheet + " " + sourceRow + "행";
    }

    public boolean importable() {
        return errorMessage == null && !alreadyImported;
    }

    public boolean duplicateCandidate() { return duplicateInFile || !matches.isEmpty(); }
    public boolean selectedByDefault() { return importable() && !duplicateCandidate(); }
}
