package com.deveopsj.spending.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;

final class CardStatementParserSupport {
    private static final DataFormatter FORMATTER = new DataFormatter();

    private CardStatementParserSupport() {}

    static Map<String, Integer> headers(Row row) {
        if (row == null) throw new IllegalArgumentException("카드 이용내역의 헤더가 없습니다.");
        Map<String, Integer> headers = new HashMap<>();
        for (Cell cell : row) {
            String header = FORMATTER.formatCellValue(cell).trim();
            headers.put(header, cell.getColumnIndex());
            headers.putIfAbsent(header.replaceAll("\\s+", ""), cell.getColumnIndex());
        }
        return headers;
    }

    static void requireHeaders(Map<String, Integer> headers, String company, String... required) {
        for (String header : required) {
            if (!headers.containsKey(header)) {
                throw new IllegalArgumentException(company + " 엑셀 양식과 일치하지 않습니다. 누락된 열: " + header);
            }
        }
    }

    static String text(Row row, int column) {
        if (row == null || row.getCell(column) == null) return "";
        Cell cell = row.getCell(column);
        if (cell.getCellType() == CellType.FORMULA) {
            throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 수식 셀은 사용할 수 없습니다.");
        }
        return FORMATTER.formatCellValue(cell).trim();
    }

    static long positiveAmount(Row row, int column) {
        String value = text(row, column).replace(",", "");
        try {
            long amount = new BigDecimal(value).longValueExact();
            if (amount <= 0) throw new ArithmeticException();
            return amount;
        } catch (RuntimeException e) {
            throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 금액은 0보다 큰 정수여야 합니다.");
        }
    }

    static String transactionKey(String company, String approvalNumber, String date,
            long amount, String merchantName) {
        String source = String.join("|", company, approvalNumber, date,
                Long.toString(amount), merchantName.replaceAll("\\s+", " ").trim());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("거래 식별값을 생성하지 못했습니다.", e);
        }
    }
}
