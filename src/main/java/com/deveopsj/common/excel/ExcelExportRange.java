package com.deveopsj.common.excel;

import java.time.LocalDate;
import java.time.YearMonth;

public record ExcelExportRange(LocalDate startDate, LocalDate endDate) {

    public ExcelExportRange {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("다운로드 기간을 입력해 주세요.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("종료일은 시작일보다 빠를 수 없습니다.");
        }
        if (endDate.isAfter(startDate.plusYears(1).minusDays(1))) {
            throw new IllegalArgumentException("엑셀 다운로드 기간은 최대 1년입니다.");
        }
    }

    public static ExcelExportRange ofMonth(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("조회 월을 입력해 주세요.");
        }
        return new ExcelExportRange(month.atDay(1), month.atEndOfMonth());
    }

    public String fileSuffix() {
        YearMonth startMonth = YearMonth.from(startDate);
        if (startDate.equals(startMonth.atDay(1)) && endDate.equals(startMonth.atEndOfMonth())) {
            return startMonth.toString();
        }
        return startDate + "_" + endDate;
    }
}
