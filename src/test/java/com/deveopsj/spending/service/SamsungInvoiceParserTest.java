package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;

import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class SamsungInvoiceParserTest {
    private final SamsungCardStatementParser parser = new SamsungCardStatementParser();
    private final YearMonth month = YearMonth.of(2026, 8);

    @Test
    void invoiceCombinesBothSheetsAndUsesPrincipalPlusFeeForThisRound() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet cash = invoiceSheet(workbook, "일시불");
            row(cash, 3, "20260807", "본인 123", "가맹점", "4500");
            row(cash, 4, "", "", "포인트 안내", "", "", "", "", "", "", "", "", "", "100");
            row(cash, 5, "", "", "합계", "4500");
            Sheet instalment = invoiceSheet(workbook, "할부");
            row(instalment, 3, "20260703", "본인 123", "할부 가맹점", "100000",
                    "100000", "", "", "2", "1", "50000", "1200");

            var rows = parser.parse(workbook, month);

            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).amount()).isEqualTo(4500);
            assertThat(rows.get(0).spendingDate()).hasToString("2026-08-07");
            assertThat(rows.get(1).amount()).isEqualTo(51200);
            assertThat(rows.get(1).spendingDate()).hasToString("2026-08-01");
            assertThat(rows.get(1).sourceLabel()).isEqualTo("할부 4행");
            assertThat(rows).allMatch(r -> r.importable());
            assertThat(parser.previewNotice(workbook, month)).contains("원금+이자/수수료", "2026-08");
        }
    }

    @Test
    void identicalPurchasesArePreservedButReuploadKeysRemainStable() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "일시불");
            row(sheet, 3, "20260807", "본인 123", "같은 가맹점", "4500");
            row(sheet, 4, "20260807", "본인 123", "같은 가맹점", "4500");
            var first = parser.parse(workbook, month);
            var second = parser.parse(workbook, month);
            assertThat(first).hasSize(2);
            assertThat(first.get(0).transactionKey()).isNotEqualTo(first.get(1).transactionKey());
            assertThat(second).extracting(r -> r.transactionKey())
                    .containsExactlyElementsOf(first.stream().map(r -> r.transactionKey()).toList());
        }
    }

    @Test
    void nextRoundGetsANewKeyButReselectingMonthCannotDuplicateSameRound() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "할부");
            row(sheet, 3, "20260703", "본인 123", "가맹점", "100000",
                    "100000", "", "", "2", "1", "50000", "");
            String firstKey = parser.parse(workbook, month).get(0).transactionKey();
            assertThat(parser.parse(workbook, month.plusMonths(1)).get(0).transactionKey()).isEqualTo(firstKey);
            sheet.getRow(3).getCell(8).setCellValue("2");
            assertThat(parser.parse(workbook, month.plusMonths(1)).get(0).transactionKey()).isNotEqualTo(firstKey);
        }
    }

    @Test
    void installmentRequiresBillingMonthAndValidRound() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "할부");
            row(sheet, 3, "20260703", "본인 123", "가맹점", "100000",
                    "100000", "", "", "2", "1", "50000", "0");
            assertThatThrownBy(() -> parser.parse(workbook, null)).hasMessageContaining("청구월");
            sheet.getRow(3).getCell(8).setCellValue("3");
            assertThatThrownBy(() -> parser.parse(workbook, month)).hasMessageContaining("회차");
        }
    }

    @Test
    void negativeOrOverflowingFeeIsRejected() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "할부");
            row(sheet, 3, "20260703", "본인 123", "가맹점", "100000",
                    "100000", "", "", "2", "1", "50000", "-10");
            assertThatThrownBy(() -> parser.parse(workbook, month)).hasMessageContaining("0 이상의 정수");
            sheet.getRow(3).getCell(9).setCellValue(Long.toString(Long.MAX_VALUE));
            sheet.getRow(3).getCell(10).setCellValue("1");
            assertThatThrownBy(() -> parser.parse(workbook, month)).hasMessageContaining("금액");
        }
    }

    @Test
    void monthBeforePurchaseAndFutureMonthCannotBeImported() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "할부");
            row(sheet, 3, "20260803", "본인 123", "가맹점", "100000",
                    "100000", "", "", "2", "1", "50000", "0");
            assertThat(parser.parse(workbook, month.minusMonths(1)).get(0).errorMessage()).contains("빠를 수 없습니다");
            assertThat(parser.parse(workbook, YearMonth.now().plusMonths(1)).get(0).errorMessage()).contains("미래");
        }
    }

    @Test
    void missingPrincipalIsNotReplacedWithFullPurchaseAmount() throws Exception {
        try (var workbook = new XSSFWorkbook()) {
            Sheet sheet = invoiceSheet(workbook, "할부");
            row(sheet, 3, "20260703", "본인 123", "가맹점", "100000",
                    "100000", "", "", "2", "1", "", "0");
            assertThatThrownBy(() -> parser.parse(workbook, month)).hasMessageContaining("원금");
        }
    }

    private Sheet invoiceSheet(XSSFWorkbook workbook, String name) {
        Sheet sheet = workbook.createSheet(name);
        row(sheet, 0, name);
        row(sheet, 2, "이용일", "이용구분", "가맹점", "이용금액", "총할부금액",
                "이용혜택", "혜택금액", "개월", "회차", "원금", "이자/수수료", "포인트명", "적립금액", "입금후잔액");
        return sheet;
    }

    private void row(Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }
}

