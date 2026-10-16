package com.deveopsj.spending.service;

import java.util.List;
import java.time.YearMonth;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardSpendingImportRow;

public interface CardStatementParser {
    CardCompany cardCompany();
    List<CardSpendingImportRow> parse(Sheet sheet);

    default List<CardSpendingImportRow> parse(Workbook workbook, YearMonth billingMonth) {
        return parse(workbook.getSheetAt(0));
    }

    default String previewNotice(Workbook workbook, YearMonth billingMonth) {
        return null;
    }
}
