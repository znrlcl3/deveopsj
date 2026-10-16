package com.deveopsj.spending.dto;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
public record SpendingExcelImportRow(int sourceRow, LocalDate spendingDate, String categoryCode,
        long amount, String memo, String transactionKey, List<SpendingDuplicateMatch> matches,
        boolean duplicateInFile, boolean alreadyImported) implements Serializable {
    public boolean duplicateCandidate() { return duplicateInFile || !matches.isEmpty(); }
    public boolean selectedByDefault() { return !alreadyImported && !duplicateCandidate(); }
}
