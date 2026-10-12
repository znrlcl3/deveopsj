package com.deveopsj.spending.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
public class KakaoBankCardStatementParser implements CardStatementParser {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private static final int HEADER_SEARCH_LIMIT = 30;

    @Override
    public CardCompany cardCompany() { return CardCompany.KAKAO_BANK; }

    @Override
    public List<CardSpendingImportRow> parse(Sheet sheet) {
        Row headerRow = findHeaderRow(sheet);
        Map<String, Integer> h = CardStatementParserSupport.headers(headerRow);
        CardStatementParserSupport.requireHeaders(h, "카카오뱅크", "거래일시", "카드번호(뒷자리)",
                "취소여부", "매출구분", "매출금액", "가맹점명");

        List<CardSpendingImportRow> rows = new ArrayList<>();
        for (int i = headerRow.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("거래일시"));
            if (dateText.isBlank()) continue;
            try {
                LocalDateTime transactionDateTime = LocalDateTime.parse(dateText, DATE_TIME);
                LocalDate date = transactionDateTime.toLocalDate();
                String cardSuffix = CardStatementParserSupport.text(row, h.get("카드번호(뒷자리)"));
                String cancellation = CardStatementParserSupport.text(row, h.get("취소여부"));
                String saleType = CardStatementParserSupport.text(row, h.get("매출구분"));
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                long amount = CardStatementParserSupport.positiveAmount(row, h.get("매출금액"));

                String error = null;
                if (!"정상".equals(cancellation)) error = "취소 거래는 첫 버전에서 지원하지 않습니다.";
                else if (!"일시불".equals(saleType)) error = "할부 또는 특수 거래는 첫 버전에서 지원하지 않습니다.";
                else if (merchant.isBlank() || cardSuffix.isBlank()) error = "가맹점명 또는 카드 식별정보가 없습니다.";
                else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                else if (date.isAfter(LocalDate.now())) error = "미래 거래는 등록할 수 없습니다.";

                String key = CardStatementParserSupport.transactionKey("KAKAO_BANK", cardSuffix,
                        transactionDateTime.toString(), amount, merchant);
                rows.add(new CardSpendingImportRow(i + 1, date, merchant, amount, key, error));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException((i + 1) + "행: 카카오뱅크 거래일시 형식이 올바르지 않습니다.");
            }
        }
        return rows;
    }

    private Row findHeaderRow(Sheet sheet) {
        int lastRow = Math.min(sheet.getLastRowNum(), HEADER_SEARCH_LIMIT - 1);
        for (int i = 0; i <= lastRow; i++) {
            Row row = sheet.getRow(i);
            if (row != null && CardStatementParserSupport.headers(row).containsKey("거래일시")) return row;
        }
        throw new IllegalArgumentException("카카오뱅크 엑셀 양식과 일치하지 않습니다. 거래내역 헤더를 찾을 수 없습니다.");
    }
}
