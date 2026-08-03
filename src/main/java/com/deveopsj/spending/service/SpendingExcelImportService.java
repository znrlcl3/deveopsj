package com.deveopsj.spending.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendingExcelImportService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_DATA_ROWS = 1_000;
    private static final List<String> REQUIRED_HEADERS = List.of("날짜", "카테고리", "금액(원)", "메모");

    private final DailySpendingRepository dailySpendingRepository;
    private final MasterCodeService masterCodeService;

    @Transactional
    public int importFile(MultipartFile file, Member member) {
        validateFile(file);
        Map<String, String> categories = categoryLookup();
        List<DailySpending> spendings = new ArrayList<>();

        try (XSSFWorkbook workbook = new XSSFWorkbook(file.getInputStream())) {
            if (workbook.getNumberOfSheets() == 0) {
                throw new IllegalArgumentException("엑셀 시트를 찾을 수 없습니다.");
            }
            Sheet sheet = workbook.getSheetAt(0);
            validateHeaders(sheet.getRow(0));
            if (sheet.getLastRowNum() > MAX_DATA_ROWS) {
                throw new IllegalArgumentException("한 번에 최대 1,000건까지 등록할 수 있습니다.");
            }
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (isEmpty(row)) continue;
                spendings.add(parseRow(row, rowIndex + 1, categories, member));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("올바른 XLSX 파일인지 확인해 주세요.", e);
        }

        if (spendings.isEmpty()) {
            throw new IllegalArgumentException("등록할 지출 내역이 없습니다.");
        }
        dailySpendingRepository.saveAll(spendings);
        return spendings.size();
    }

    private DailySpending parseRow(Row row, int displayRow, Map<String, String> categories, Member member) {
        rejectFormula(row, displayRow);
        LocalDate date = parseDate(cellText(row, 0), displayRow);
        if (date.isAfter(LocalDate.now())) {
            throw rowError(displayRow, "지출 날짜는 미래일 수 없습니다.");
        }
        String categoryInput = cellText(row, 1).trim();
        String categoryCode = categories.get(categoryInput);
        if (categoryCode == null) {
            throw rowError(displayRow, "사용할 수 없는 카테고리입니다: " + categoryInput);
        }
        long amount = parseAmount(cellText(row, 2), displayRow);
        String memo = cellText(row, 3).trim();
        if (memo.isEmpty()) {
            throw rowError(displayRow, "메모를 입력해 주세요.");
        }
        if (memo.length() > 200) {
            throw rowError(displayRow, "메모는 200자 이하여야 합니다.");
        }
        return DailySpending.builder()
                .member(member)
                .spendingDate(date)
                .categoryCode(categoryCode)
                .amount(amount)
                .memo(memo)
                .build();
    }

    private Map<String, String> categoryLookup() {
        List<MasterCodeDto> activeCodes = masterCodeService.getActiveCodesByGroup("SPENDING_CAT");
        if (activeCodes.isEmpty()) {
            throw new IllegalStateException("사용 가능한 지출 카테고리가 없습니다.");
        }
        Map<String, String> lookup = new HashMap<>();
        for (MasterCodeDto code : activeCodes) {
            lookup.put(code.getCodeId(), code.getCodeId());
            lookup.put(code.getCodeName(), code.getCodeId());
        }
        return lookup;
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드할 엑셀 파일을 선택해 주세요.");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx")) {
            throw new IllegalArgumentException("XLSX 파일만 업로드할 수 있습니다.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("엑셀 파일은 5MB 이하여야 합니다.");
        }
    }

    private void validateHeaders(Row header) {
        if (header == null) throw new IllegalArgumentException("엑셀 헤더가 없습니다.");
        for (int i = 0; i < REQUIRED_HEADERS.size(); i++) {
            if (!REQUIRED_HEADERS.get(i).equals(cellText(header, i).trim())) {
                throw new IllegalArgumentException("엑셀 헤더 형식이 올바르지 않습니다. 필요한 열: "
                        + String.join(", ", REQUIRED_HEADERS));
            }
        }
    }

    private LocalDate parseDate(String value, int row) {
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw rowError(row, "날짜는 yyyy-MM-dd 형식이어야 합니다.");
        }
    }

    private long parseAmount(String value, int row) {
        try {
            String normalized = value.replace(",", "").trim();
            long amount = new BigDecimal(normalized).longValueExact();
            if (amount <= 0) throw new ArithmeticException();
            return amount;
        } catch (RuntimeException e) {
            throw rowError(row, "금액은 0보다 큰 정수여야 합니다.");
        }
    }

    private void rejectFormula(Row row, int displayRow) {
        for (int column = 0; column < REQUIRED_HEADERS.size(); column++) {
            Cell cell = row.getCell(column);
            if (cell != null && cell.getCellType() == CellType.FORMULA) {
                throw rowError(displayRow, "수식 셀은 사용할 수 없습니다.");
            }
        }
    }

    private boolean isEmpty(Row row) {
        if (row == null) return true;
        for (int column = 0; column < REQUIRED_HEADERS.size(); column++) {
            if (!cellText(row, column).isBlank()) return false;
        }
        return true;
    }

    private String cellText(Row row, int column) {
        if (row == null || row.getCell(column) == null) return "";
        return new DataFormatter().formatCellValue(row.getCell(column));
    }

    private IllegalArgumentException rowError(int row, String message) {
        return new IllegalArgumentException(row + "행: " + message);
    }
}
