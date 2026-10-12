package com.deveopsj.spending.service;

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
public class ShinhanCardStatementParser implements CardStatementParser {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm");

    @Override
    public CardCompany cardCompany() { return CardCompany.SHINHAN; }

    @Override
    public List<CardSpendingImportRow> parse(Sheet sheet) {
        Map<String, Integer> h = CardStatementParserSupport.headers(sheet.getRow(0));
        CardStatementParserSupport.requireHeaders(h, "신한카드", "거래일", "가맹점명", "승인번호",
                "금액", "매입구분", "이용구분", "취소상태");
        List<CardSpendingImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("거래일"));
            if (dateText.isBlank()) continue;
            try {
                LocalDateTime dateTime = LocalDateTime.parse(dateText, DATE_TIME);
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                long amount = CardStatementParserSupport.positiveAmount(row, h.get("금액"));
                String purchaseStatus = CardStatementParserSupport.text(row, h.get("매입구분"));
                String cancelStatus = CardStatementParserSupport.text(row, h.get("취소상태"));
                String error = null;
                if (!cancelStatus.isBlank()) error = "취소 거래는 첫 버전에서 지원하지 않습니다.";
                else if (!("승인".equals(purchaseStatus) || "결제확정".equals(purchaseStatus)))
                    error = "지원하지 않는 매입 상태입니다: " + purchaseStatus;
                else if (merchant.isBlank() || approval.isBlank()) error = "가맹점명 또는 승인번호가 없습니다.";
                else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                else if (dateTime.toLocalDate().isAfter(java.time.LocalDate.now())) error = "미래 거래는 등록할 수 없습니다.";
                String key = CardStatementParserSupport.transactionKey("SHINHAN", approval,
                        dateTime.toString(), amount, merchant);
                rows.add(new CardSpendingImportRow(i + 1, dateTime.toLocalDate(), merchant,
                        amount, key, error));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException((i + 1) + "행: 신한카드 거래일 형식이 올바르지 않습니다.");
            }
        }
        return rows;
    }
}
