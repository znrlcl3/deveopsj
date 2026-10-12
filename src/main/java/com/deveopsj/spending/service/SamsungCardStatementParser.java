package com.deveopsj.spending.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.springframework.stereotype.Component;

import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardSpendingImportRow;

@Component
public class SamsungCardStatementParser implements CardStatementParser {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Override
    public CardCompany cardCompany() { return CardCompany.SAMSUNG; }

    @Override
    public List<CardSpendingImportRow> parse(Sheet sheet) {
        Map<String, Integer> h = CardStatementParserSupport.headers(sheet.getRow(0));
        CardStatementParserSupport.requireHeaders(h, "삼성카드", "매출상품명", "할부개월수", "매출일자",
                "매출금액(원)", "가맹점명", "가맹점번호", "승인번호");
        List<CardSpendingImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("매출일자"));
            if (dateText.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(dateText, DATE);
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                long amount = CardStatementParserSupport.positiveAmount(row, h.get("매출금액(원)"));
                String product = CardStatementParserSupport.text(row, h.get("매출상품명"));
                String installment = CardStatementParserSupport.text(row, h.get("할부개월수"));
                String error = null;
                if (!"일시불".equals(product) || !(installment.isBlank() || "0".equals(installment)))
                    error = "할부 또는 특수 거래는 첫 버전에서 지원하지 않습니다.";
                else if (merchant.isBlank() || approval.isBlank()) error = "가맹점명 또는 승인번호가 없습니다.";
                else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                else if (date.isAfter(LocalDate.now())) error = "미래 거래는 등록할 수 없습니다.";
                String key = CardStatementParserSupport.transactionKey("SAMSUNG", approval,
                        date.toString(), amount, merchant);
                rows.add(new CardSpendingImportRow(i + 1, date, merchant, amount, key, error));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException((i + 1) + "행: 삼성카드 매출일자 형식이 올바르지 않습니다.");
            }
        }
        return rows;
    }
}
