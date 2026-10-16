package com.deveopsj.spending.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.Objects;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.dao.DataIntegrityViolationException;
import com.deveopsj.spending.dto.SpendingExcelImportPreview;
import com.deveopsj.spending.dto.SpendingExcelImportRow;

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
        var preview = preview(file, member, null);
        if (preview.rows().stream().anyMatch(row -> row.duplicateCandidate() || row.alreadyImported())) {
            throw new IllegalArgumentException("중복 후보가 있습니다. 엑셀 미리보기에서 등록할 행을 확인해 주세요.");
        }
        return confirm(preview, java.util.stream.IntStream.range(0, preview.rows().size()).boxed().toList(), member);
    }

    @Transactional(readOnly = true)
    public SpendingExcelImportPreview preview(MultipartFile file, Member member, String month) {
        List<ParsedRow> parsed = parseFile(file, member);
        List<DailySpending> existing = existing(member, parsed.stream().map(row -> row.entity().getSpendingDate()).toList());
        var index = SpendingDuplicateSupport.index(existing);
        Set<String> importedKeys = new HashSet<>();
        for (DailySpending row : existing) {
            if ("EXCEL".equals(row.getImportSource())) importedKeys.add(row.getImportTransactionKey());
        }
        String fileHash = fileHash(file);
        List<String> fileKeys = parsed.stream().map(row -> importKey(row, fileHash)).toList();
        for (DailySpending imported : dailySpendingRepository.findByMemberMemberIdAndImportSourceAndImportTransactionKeyIn(
                member.getMemberId(), "EXCEL", fileKeys)) importedKeys.add(imported.getImportTransactionKey());
        Set<SpendingDuplicateSupport.Key> seen = new HashSet<>();
        List<SpendingExcelImportRow> rows = new ArrayList<>();
        for (ParsedRow parsedRow : parsed) {
            DailySpending row = parsedRow.entity();
            var key = SpendingDuplicateSupport.key(row.getSpendingDate(), row.getAmount(), row.getMemo());
            String importKey = importKey(parsedRow, fileHash);
            rows.add(new SpendingExcelImportRow(parsedRow.sourceRow(), row.getSpendingDate(), row.getCategoryCode(),
                    row.getAmount(), row.getMemo(), importKey,
                    SpendingDuplicateSupport.matches(index.getOrDefault(key, List.of())), !seen.add(key),
                    importedKeys.contains(importKey)));
        }
        return new SpendingExcelImportPreview(UUID.randomUUID().toString(), member.getMemberId(),
                file.getOriginalFilename(), month, List.copyOf(rows));
    }

    @Transactional
    public int confirm(SpendingExcelImportPreview preview, List<Integer> selectedRows, Member member) {
        if (preview == null || !Objects.equals(preview.memberId(), member.getMemberId())) {
            throw new IllegalArgumentException("미리보기 정보가 만료되었습니다. 파일을 다시 선택해 주세요.");
        }
        if (selectedRows == null || selectedRows.isEmpty()) throw new IllegalArgumentException("등록할 행을 선택해 주세요.");
        Set<Integer> indexes = new java.util.LinkedHashSet<>(selectedRows);
        for (Integer index : indexes) {
            if (index == null || index < 0 || index >= preview.rows().size())
                throw new IllegalArgumentException("선택한 행이 올바르지 않습니다.");
        }
        var categories = categoryLookup();
        var selected = indexes.stream().map(index -> preview.rows().get(index)).toList();
        var existing = existing(member, selected.stream().map(row -> row.spendingDate()).toList());
        var duplicates = SpendingDuplicateSupport.index(existing);
        Set<String> importedKeys = new HashSet<>();
        for (DailySpending row : existing) {
            if ("EXCEL".equals(row.getImportSource())) importedKeys.add(row.getImportTransactionKey());
        }
        for (DailySpending imported : dailySpendingRepository.findByMemberMemberIdAndImportSourceAndImportTransactionKeyIn(
                member.getMemberId(), "EXCEL", selected.stream().map(row -> row.transactionKey()).toList()))
            importedKeys.add(imported.getImportTransactionKey());
        List<DailySpending> entities = new ArrayList<>();
        for (var row : selected) {
            if (row.alreadyImported() || importedKeys.contains(row.transactionKey()))
                throw new IllegalArgumentException(row.sourceRow() + "행은 이미 등록한 엑셀 내역입니다. 미리보기를 다시 확인해 주세요.");
            var key = SpendingDuplicateSupport.key(row.spendingDate(), row.amount(), row.memo());
            if (!row.duplicateCandidate() && duplicates.containsKey(key))
                throw new IllegalArgumentException("미리보기 이후 같은 내역이 등록됐습니다. 파일을 다시 올려 중복 후보를 확인해 주세요.");
            if (!categories.containsKey(row.categoryCode()))
                throw new IllegalArgumentException(row.sourceRow() + "행의 카테고리는 더 이상 사용할 수 없습니다.");
            entities.add(DailySpending.builder().member(member).spendingDate(row.spendingDate())
                    .categoryCode(row.categoryCode()).amount(row.amount()).memo(row.memo())
                    .importSource("EXCEL").importTransactionKey(row.transactionKey()).build());
        }
        try {
            dailySpendingRepository.saveAll(entities);
            dailySpendingRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 등록한 엑셀 내역이 있습니다. 파일을 다시 올려 확인해 주세요.", e);
        }
        return entities.size();
    }

    private List<DailySpending> existing(Member member, List<LocalDate> dates) {
        LocalDate start = dates.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate end = dates.stream().max(LocalDate::compareTo).orElseThrow();
        return dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(member.getMemberId(), start, end);
    }

    private String fileHash(MultipartFile file) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.getBytes()));
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("파일 식별값을 생성하지 못했습니다.", e);
        }
    }

    private String importKey(ParsedRow row, String fileHash) {
        return CardStatementParserSupport.transactionKey("EXCEL", fileHash + ":" + row.sourceRow(),
                row.entity().getSpendingDate().toString(), row.entity().getAmount(), row.entity().getMemo());
    }

    private record ParsedRow(int sourceRow, DailySpending entity) {}

    private List<ParsedRow> parseFile(MultipartFile file, Member member) {
        validateFile(file);
        Map<String, String> categories = categoryLookup();
        List<ParsedRow> spendings = new ArrayList<>();

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
                spendings.add(new ParsedRow(rowIndex + 1, parseRow(row, rowIndex + 1, categories, member)));
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("올바른 XLSX 파일인지 확인해 주세요.", e);
        }

        if (spendings.isEmpty()) {
            throw new IllegalArgumentException("등록할 지출 내역이 없습니다.");
        }
        return spendings;
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
