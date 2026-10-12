package com.deveopsj.spending.dto;

import java.io.Serializable;
import java.time.LocalDate;

public record CardSpendingImportRow(
        int sourceRow,
        LocalDate spendingDate,
        String merchantName,
        long amount,
        String transactionKey,
        String errorMessage) implements Serializable {

    public boolean importable() {
        return errorMessage == null;
    }
}
