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
        java.util.Set<String> cancelledApprovals = new java.util.HashSet<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (CardStatementParserSupport.text(row, h.get("거래일")).isBlank()) continue;
            long amount = signedAmount(row, h.get("금액"));
            if (amount < 0 || !CardStatementParserSupport.text(row, h.get("취소상태")).isBlank()) {
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                if (!approval.isBlank()) cancelledApprovals.add(approvalIdentity(row, h));
            }
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("거래일"));
            if (dateText.isBlank()) continue;
            try {
                LocalDateTime dateTime = LocalDateTime.parse(dateText, DATE_TIME);
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                long amount = signedAmount(row, h.get("금액"));
                String purchaseStatus = CardStatementParserSupport.text(row, h.get("매입구분"));
                String cancelStatus = CardStatementParserSupport.text(row, h.get("취소상태"));
                String error = null;
                if (amount < 0 || !cancelStatus.isBlank())
                    error = "결제취소·환불 내역입니다. 등록에서 제외되며 기존 지출은 자동 차감되지 않습니다. 아래 링크에서 원거래를 확인하고 취소를 반영해 주세요.";
                else if (cancelledApprovals.contains(approvalIdentity(row, h)))
                    error = "같은 승인번호·가맹점의 취소 내역이 있습니다. 전액·부분취소 여부를 확인한 뒤 잔여 지출을 별도로 등록해 주세요.";
                else if (amount == 0) error = "금액이 0인 내역은 등록할 수 없습니다.";
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

    @Override
    public String previewNotice(org.apache.poi.ss.usermodel.Workbook workbook, java.time.YearMonth billingMonth) {
        return "신한카드 음수 금액과 취소 상태가 있는 거래는 등록에서 제외합니다. 같은 파일의 동일 승인번호·가맹점 거래도 확인 대상으로 제외합니다. "
                + "음수 취소 건의 '기존 지출 찾아 취소 반영'에서 후보를 확인할 수 있습니다. 이미 수동으로 반영한 취소는 다시 적용하지 마세요.";
    }

    private String approvalIdentity(Row row, Map<String, Integer> headers) {
        return CardStatementParserSupport.text(row, headers.get("승인번호")) + "\u0000"
                + CardStatementParserSupport.text(row, headers.get("가맹점명")).replaceAll("\\s+", " ");
    }

    private long signedAmount(Row row, int column) {
        String value = CardStatementParserSupport.text(row, column).replace(",", "");
        try {
            return new java.math.BigDecimal(value).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 금액은 정수여야 합니다. 취소 금액은 음수로 입력할 수 있습니다.");
        }
    }
}
