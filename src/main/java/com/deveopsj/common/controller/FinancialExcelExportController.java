package com.deveopsj.common.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

import com.deveopsj.common.excel.ExcelExportRange;
import com.deveopsj.common.excel.FinancialExcelExportService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FinancialExcelExportController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final FinancialExcelExportService exportService;

    @GetMapping("/income/export")
    public ResponseEntity<byte[]> incomes(@RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Member member) {
        ExcelExportRange range = range(month, startDate, endDate);
        return file(exportService.incomes(member, range), "수입내역_" + range.fileSuffix() + ".xlsx");
    }

    @GetMapping("/spending/export")
    public ResponseEntity<byte[]> spendings(@RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Member member) {
        ExcelExportRange range = range(month, startDate, endDate);
        return file(exportService.spendings(member, range), "지출내역_" + range.fileSuffix() + ".xlsx");
    }

    @GetMapping("/trades/export")
    public ResponseEntity<byte[]> trades(@RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Member member) {
        ExcelExportRange range = range(month, startDate, endDate);
        return file(exportService.trades(member, range), "주식ETF매매내역_" + range.fileSuffix() + ".xlsx");
    }

    @GetMapping("/savings/export")
    public ResponseEntity<byte[]> savings(@RequestParam(required = false) YearMonth month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Member member) {
        ExcelExportRange range = range(month, startDate, endDate);
        return file(exportService.savings(member, range), "납입내역_" + range.fileSuffix() + ".xlsx");
    }

    private ExcelExportRange range(YearMonth month, LocalDate startDate, LocalDate endDate) {
        return month != null ? ExcelExportRange.ofMonth(month) : new ExcelExportRange(startDate, endDate);
    }

    private ResponseEntity<byte[]> file(byte[] body, String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentLength(body.length)
                .body(body);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> invalidRange(IllegalArgumentException exception) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }
}
