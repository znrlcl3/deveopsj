package com.deveopsj.spending.service;

import java.util.List;

import org.apache.poi.ss.usermodel.Sheet;

import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardSpendingImportRow;

public interface CardStatementParser {
    CardCompany cardCompany();
    List<CardSpendingImportRow> parse(Sheet sheet);
}
