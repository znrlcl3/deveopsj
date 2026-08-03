package com.deveopsj.common.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;

import org.junit.jupiter.api.Test;

class ExcelExportRangeTest {

    @Test
    void 조회월을_월의_시작일과_종료일로_변환한다() {
        ExcelExportRange range = ExcelExportRange.ofMonth(YearMonth.of(2026, 2));
        assertThat(range.startDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(range.endDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(range.fileSuffix()).isEqualTo("2026-02");
    }

    @Test
    void 기간은_최대_1년까지만_허용한다() {
        assertThatThrownBy(() -> new ExcelExportRange(
                LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("엑셀 다운로드 기간은 최대 1년입니다.");
    }

    @Test
    void 종료일은_시작일보다_빠를수없다() {
        assertThatThrownBy(() -> new ExcelExportRange(
                LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 31)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
