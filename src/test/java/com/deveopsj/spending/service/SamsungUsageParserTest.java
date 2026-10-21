package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.*;
import java.io.ByteArrayOutputStream;
import java.time.YearMonth;
import java.util.List;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import static org.mockito.Mockito.mock;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.repository.DailySpendingRepository;

class SamsungUsageParserTest {
    private final SamsungCardStatementParser parser = new SamsungCardStatementParser();

    @Test
    void skipsSummarySheetAndUsesApprovalDateRegardlessOfPaymentDateOrSelectedMonth() throws Exception {
        try (var book = workbook()) {
            var rows = parser.parse(book, YearMonth.of(2026, 8));
            assertThat(rows).hasSize(2);
            assertThat(rows.get(0).importable()).isTrue();
            assertThat(rows.get(0).spendingDate()).hasToString("2026-07-03");
            assertThat(rows.get(0).amount()).isEqualTo(4500);
            assertThat(rows.get(0).sourceLabel()).isEqualTo("■ 국내이용내역 2행");
            assertThat(rows.get(1).importable()).isTrue();
            assertThat(rows.get(1).selectedByDefault()).isFalse();
            assertThat(rows.get(1).installment().months()).isEqualTo(3);
            assertThat(rows.get(1).installment().firstPaymentDate()).hasToString("2026-08-13");
            assertThat(parser.previewNotice(book, null)).contains("예상 원금", "취소", "수수료");
        }
    }

    @Test
    void actualUploadServiceSelectsDetailSheetAndPropagatesBlockedInstallmentNotice() throws Exception {
        var codes = mock(MasterCodeService.class);
        org.mockito.Mockito.when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                com.deveopsj.common.dto.MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        var service = new CardSpendingImportService(mock(DailySpendingRepository.class),
                codes, mock(SpendingCategoryPredictionService.class), List.of(parser));
        try (var book = workbook(); var out = new ByteArrayOutputStream()) {
            book.write(out);
            var preview = service.preview(CardCompany.SAMSUNG,
                    new MockMultipartFile("file", "usage.xlsx", null, out.toByteArray()));
            assertThat(preview.rows()).hasSize(2);
            assertThat(preview.rows().stream().filter(r -> r.importable())).hasSize(2);
            assertThat(preview.notice()).contains("카드이용내역조회", "할부");
            assertThatThrownBy(() -> service.confirm(new com.deveopsj.spending.dto.CardImportPreview(
                    preview.token(), CardCompany.SAMSUNG, "usage.xlsx", preview.rows(), preview.notice(), 7L),
                    List.of(1), java.util.Map.of(), member())).hasMessageContaining("확인 체크");
        }
    }

    @Test
    void cancelledNegativeRowAndItsOriginalApprovalAreBothBlocked() throws Exception {
        try (var book = workbook()) {
            var sheet = book.getSheetAt(1);
            data(sheet, 3, "2026.07.03", "-4500", "일시불", "0", "APP001", "취소");
            var rows = parser.parse(book, null);
            assertThat(rows.get(0).importable()).isFalse();
            assertThat(rows.get(0).errorMessage()).contains("취소");
            assertThat(rows.get(2).importable()).isFalse();
            assertThat(rows.get(2).amount()).isEqualTo(-4500);
        }
    }

    @Test
    void stableKeyMatchesLegacyBusinessExportAndIgnoresPaymentDate() throws Exception {
        try (var book = workbook(); var old = new XSSFWorkbook()) {
            var sheet = old.createSheet();
            row(sheet, 0, "매출상품명", "할부개월수", "매출일자", "매출금액(원)", "가맹점명", "가맹점번호", "승인번호");
            row(sheet, 1, "일시불", "0", "20260703", "4500", "테스트 가맹점", "123", "APP001");
            String key = parser.parse(book, null).get(0).transactionKey();
            assertThat(key).isEqualTo(parser.parse(sheet).get(0).transactionKey());
            book.getSheetAt(1).getRow(1).getCell(11).setCellValue("");
            assertThat(parser.parse(book, YearMonth.of(2026, 9)).get(0).transactionKey()).isEqualTo(key);
        }
    }

    @Test
    void invalidCalendarDateAndNonIntegerAmountAreRejected() throws Exception {
        try (var book = workbook()) {
            var date = book.getSheetAt(1).getRow(1).getCell(2);
            date.setCellValue("2026.02.30");
            assertThatThrownBy(() -> parser.parse(book, null)).hasMessageContaining("승인일자");
            date.setCellValue("2026.07.03");
            book.getSheetAt(1).getRow(1).getCell(5).setCellValue("4500.5");
            assertThatThrownBy(() -> parser.parse(book, null)).hasMessageContaining("정수");
        }
    }

    @Test
    void missingCancellationColumnFailsInsteadOfSilentlyImporting() throws Exception {
        try (var book = workbook()) {
            book.getSheetAt(1).getRow(0).getCell(9).setCellValue("알 수 없는 열");
            assertThatThrownBy(() -> parser.parse(book, null)).hasMessageContaining("취소여부");
        }
    }

    @Test
    void missingApprovalAndFutureDateCannotBeImported() throws Exception {
        try (var book = workbook()) {
            var row = book.getSheetAt(1).getRow(1);
            row.getCell(8).setCellValue("");
            assertThat(parser.parse(book, null).get(0).errorMessage()).contains("승인번호");
            row.getCell(8).setCellValue("APP001");
            row.getCell(2).setCellValue(java.time.LocalDate.now().plusDays(1)
                    .format(java.time.format.DateTimeFormatter.ofPattern("uuuu.MM.dd")));
            assertThat(parser.parse(book, null).get(0).errorMessage()).contains("미래");
        }
    }

    private XSSFWorkbook workbook() {
        var book = new XSSFWorkbook();
        var summary = book.createSheet("■ 카드이용내역");
        row(summary, 0, "조회기간", "이용지역", "이용구분", "금액합계(원)", "건수합계(건)");
        row(summary, 1, "2026.07.01 ~ 2026.07.31", "국내", "일시불+할부", "34500", "2");
        var detail = book.createSheet("■ 국내이용내역");
        row(detail, 0, "카드번호", "본인가족구분", "승인일자", "승인시각", "가맹점명", "승인금액(원)",
                "일시불할부구분", "할부개월", "승인번호", "취소여부", "사용포인트", "결제일");
        data(detail, 1, "2026.07.03", "4500", "일시불", "0", "APP001", "-");
        data(detail, 2, "2026.07.05", "30000", "할부", "3", "APP002", "-");
        return book;
    }

    private void data(Sheet sheet, int n, String date, String amount, String type, String months, String approval, String cancel) {
        row(sheet, n, "0000******1234", "본인", date, "12:00:00", "테스트 가맹점", amount,
                type, months, approval, cancel, "0", "20260813");
    }

    private void row(Sheet sheet, int n, String... values) {
        var row = sheet.createRow(n);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private com.deveopsj.member.entity.Member member() {
        var member = new com.deveopsj.member.entity.Member();
        member.setMemberId(7L);
        return member;
    }
}
