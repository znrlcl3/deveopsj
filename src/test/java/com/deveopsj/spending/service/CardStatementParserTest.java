package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class CardStatementParserTest {

    @Test
    void 신한카드_양식을_공통_지출행으로_변환한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("카드이용내역");
            row(sheet, 0, "거래일", "가맹점명", "승인번호", "금액", "매입구분", "이용구분", "취소상태");
            row(sheet, 1, "2026.08.29 21:09", "테스트 가맹점", "04156423", "38800", "결제확정", "일시불", "");

            var rows = new ShinhanCardStatementParser().parse(sheet);

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.spendingDate()).hasToString("2026-08-29");
                assertThat(row.amount()).isEqualTo(38_800L);
                assertThat(row.transactionKey()).hasSize(64);
                assertThat(row.importable()).isTrue();
            });
        }
    }

    @Test
    void 삼성카드_양식을_공통_지출행으로_변환한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("개인사업자용 카드 이용내역");
            row(sheet, 0, "매출상품명", "할부개월수", "매출일자", "매출금액(원)",
                    "가맹점명", "가맹점번호", "승인번호");
            row(sheet, 1, "일시불", "0", "20260807", "4700", "테스트 가맹점", "200625223", "68247816");

            var rows = new SamsungCardStatementParser().parse(sheet);

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.spendingDate()).hasToString("2026-08-07");
                assertThat(row.amount()).isEqualTo(4_700L);
                assertThat(row.importable()).isTrue();
            });
        }
    }

    @Test
    void 카카오뱅크_B열에서_시작하는_양식을_공통_지출행으로_변환한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("카카오뱅크 카드이용내역");
            rowFromColumn(sheet, 16, 1, "거래일시", "카드번호\n(뒷자리)", "취소여부", "매출구분",
                    "매출금액", "부가세액", "가맹점명", "사업자등록번호", "과세유형");
            rowFromColumn(sheet, 17, 1, "2026.08.07 12:34:56", "1234", "정상", "일시불",
                    "55130", "5012", "테스트 가맹점", "1234567890", "일반과세자");

            var rows = new KakaoBankCardStatementParser().parse(sheet);

            assertThat(rows).singleElement().satisfies(row -> {
                assertThat(row.sourceRow()).isEqualTo(18);
                assertThat(row.spendingDate()).hasToString("2026-08-07");
                assertThat(row.amount()).isEqualTo(55_130L);
                assertThat(row.transactionKey()).hasSize(64);
                assertThat(row.importable()).isTrue();
            });
        }
    }

    @Test
    void 카카오뱅크_취소거래는_미리보기에서_등록불가로_표시한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            rowFromColumn(sheet, 16, 1, "거래일시", "카드번호(뒷자리)", "취소여부", "매출구분",
                    "매출금액", "가맹점명");
            rowFromColumn(sheet, 17, 1, "2026.08.07 12:34:56", "1234", "취소", "일시불",
                    "10000", "테스트");

            assertThat(new KakaoBankCardStatementParser().parse(sheet).get(0).importable()).isFalse();
        }
    }

    @Test
    void 선택한_카드사와_헤더가_다르면_거부한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            row(sheet, 0, "매출상품명", "매출일자", "매출금액(원)");

            assertThatThrownBy(() -> new ShinhanCardStatementParser().parse(sheet))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("신한카드 엑셀 양식");
        }
    }

    @Test
    void 지원하지않는_할부는_미리보기에서_등록불가로_표시한다() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet();
            row(sheet, 0, "매출상품명", "할부개월수", "매출일자", "매출금액(원)",
                    "가맹점명", "가맹점번호", "승인번호");
            row(sheet, 1, "할부", "3", "20260807", "30000", "테스트", "1", "12345678");

            assertThat(new SamsungCardStatementParser().parse(sheet).get(0).importable()).isFalse();
        }
    }

    private void row(org.apache.poi.ss.usermodel.Sheet sheet, int index, String... values) {
        var row = sheet.createRow(index);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private void rowFromColumn(org.apache.poi.ss.usermodel.Sheet sheet, int rowIndex,
            int columnIndex, String... values) {
        var row = sheet.createRow(rowIndex);
        for (int i = 0; i < values.length; i++) row.createCell(columnIndex + i).setCellValue(values[i]);
    }
}
