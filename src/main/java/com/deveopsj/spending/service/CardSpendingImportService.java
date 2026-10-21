package com.deveopsj.spending.service;

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.time.YearMonth;
import java.time.LocalDate;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.poifs.crypt.Decryptor;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardImportPreview;
import com.deveopsj.spending.dto.CardSpendingImportRow;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;
import com.deveopsj.spending.repository.RecurringExpenseRepository;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.dto.CardInstallmentConfirmation;

@Service
public class CardSpendingImportService {
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final int MAX_ROWS = 1_000;

    private final DailySpendingRepository repository;
    private final MasterCodeService masterCodeService;
    private final SpendingCategoryPredictionService categoryPredictionService;
    private final RecurringExpenseRepository recurringRepository;
    private final Map<CardCompany, CardStatementParser> parsers = new EnumMap<>(CardCompany.class);

    public CardSpendingImportService(DailySpendingRepository repository,
            MasterCodeService masterCodeService,
            SpendingCategoryPredictionService categoryPredictionService,
            List<CardStatementParser> parserList) {
        this(repository, masterCodeService, categoryPredictionService, parserList, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public CardSpendingImportService(DailySpendingRepository repository,
            MasterCodeService masterCodeService, SpendingCategoryPredictionService categoryPredictionService,
            List<CardStatementParser> parserList, RecurringExpenseRepository recurringRepository) {
        this.repository = repository;
        this.masterCodeService = masterCodeService;
        this.categoryPredictionService = categoryPredictionService;
        this.recurringRepository = recurringRepository;
        for (CardStatementParser parser : parserList) parsers.put(parser.cardCompany(), parser);
    }

    public CardImportPreview preview(CardCompany company, MultipartFile file) {
        return preview(company, file, null);
    }

    public CardImportPreview preview(CardCompany company, MultipartFile file, String filePassword) {
        return preview(company, file, filePassword, null);
    }

    public CardImportPreview preview(CardCompany company, MultipartFile file, String filePassword,
            YearMonth billingMonth) {
        return preview(company, file, filePassword, billingMonth, null);
    }

    @Transactional(readOnly = true)
    public CardImportPreview preview(CardCompany company, MultipartFile file, String filePassword,
            YearMonth billingMonth, Member member) {
        validateFile(file);
        CardStatementParser parser = parsers.get(company);
        if (parser == null) throw new IllegalArgumentException("지원하지 않는 카드사입니다.");
        try (XSSFWorkbook workbook = openWorkbook(file, filePassword)) {
            if (workbook.getNumberOfSheets() == 0) throw new IllegalArgumentException("엑셀 시트를 찾을 수 없습니다.");
            List<CardSpendingImportRow> rows = parser.parse(workbook, billingMonth);
            if (rows.isEmpty()) throw new IllegalArgumentException("가져올 카드 이용내역이 없습니다.");
            if (rows.size() > MAX_ROWS) throw new IllegalArgumentException("한 번에 최대 1,000건까지 확인할 수 있습니다.");
            if (member != null) rows = markDuplicates(company, rows, member);
            return new CardImportPreview(UUID.randomUUID().toString(), company,
                    safeFilename(file.getOriginalFilename()), List.copyOf(rows),
                    parser.previewNotice(workbook, billingMonth), member == null ? null : member.getMemberId());
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("올바른 XLSX 파일인지 확인해 주세요.", e);
        }
    }

    private XSSFWorkbook openWorkbook(MultipartFile file, String filePassword) throws IOException {
        try (InputStream raw = file.getInputStream();
                InputStream checked = FileMagic.prepareToCheckMagic(raw)) {
            FileMagic magic = FileMagic.valueOf(checked);
            if (magic == FileMagic.OLE2) return openEncryptedWorkbook(checked, filePassword);
            if (magic != FileMagic.OOXML)
                throw new IllegalArgumentException("올바른 XLSX 파일인지 확인해 주세요.");
            return new XSSFWorkbook(checked);
        }
    }

    private XSSFWorkbook openEncryptedWorkbook(InputStream input, String filePassword) throws IOException {
        try (POIFSFileSystem fileSystem = new POIFSFileSystem(input)) {
            if (!fileSystem.getRoot().hasEntry(Decryptor.DEFAULT_POIFS_ENTRY))
                throw new IllegalArgumentException("올바른 XLSX 파일인지 확인해 주세요.");
            if (filePassword == null || filePassword.isBlank())
                throw new IllegalArgumentException("암호화된 엑셀 파일의 비밀번호를 입력해 주세요.");
            EncryptionInfo encryptionInfo = new EncryptionInfo(fileSystem);
            Decryptor decryptor = Decryptor.getInstance(encryptionInfo);
            if (!decryptor.verifyPassword(filePassword))
                throw new IllegalArgumentException("엑셀 파일 비밀번호가 올바르지 않습니다.");
            try (InputStream decrypted = decryptor.getDataStream(fileSystem)) {
                return new XSSFWorkbook(decrypted);
            }
        } catch (GeneralSecurityException e) {
            throw new IllegalArgumentException("암호화된 엑셀 파일을 열 수 없습니다.", e);
        }
    }

    @Transactional
    public ImportResult confirm(CardImportPreview preview, List<Integer> selectedRows,
            Map<Integer, String> categoryByIndex, Member member) {
        return confirm(preview, selectedRows, categoryByIndex, Map.of(), member);
    }

    @Transactional
    public ImportResult confirm(CardImportPreview preview, List<Integer> selectedRows,
            Map<Integer, String> categoryByIndex, Map<Integer, CardInstallmentConfirmation> installments, Member member) {
        if (preview == null) throw new IllegalArgumentException("미리보기 정보가 만료되었습니다. 파일을 다시 선택해 주세요.");
        if (!Objects.equals(preview.memberId(), member.getMemberId()))
            throw new IllegalArgumentException("본인의 미리보기만 등록할 수 있습니다. 파일을 다시 선택해 주세요.");
        if (selectedRows == null || selectedRows.isEmpty()) throw new IllegalArgumentException("등록할 내역을 선택해 주세요.");
        List<MasterCodeDto> activeCategories = masterCodeService.getActiveCodesByGroup("SPENDING_CAT");
        if (activeCategories.isEmpty()) throw new IllegalStateException("사용 가능한 지출 카테고리가 없습니다.");
        Set<String> categories = new HashSet<>();
        for (MasterCodeDto code : activeCategories) categories.add(code.getCodeId());

        Set<Integer> uniqueIndexes = new HashSet<>(selectedRows);
        var currentMatches = SpendingDuplicateSupport.index(existing(preview.rows(), member));
        Set<String> merchantsForPrediction = new java.util.LinkedHashSet<>();
        int scheduledCount = 0;
        for (Integer index : uniqueIndexes) {
            if (index == null || index < 0 || index >= preview.rows().size())
                throw new IllegalArgumentException("선택한 업로드 행이 올바르지 않습니다.");
            CardSpendingImportRow row = preview.rows().get(index);
            if (!row.importable()) throw new IllegalArgumentException(row.sourceRow() + "행은 등록할 수 없는 거래입니다.");
            if (row.installment() != null) {
                var choice = installments.get(index);
                if (choice == null || !choice.reviewed() || choice.firstPaymentDate() == null)
                    throw new IllegalArgumentException(row.sourceRow() + "행: 첫 청구일과 예상액 확인 체크가 필요합니다.");
                if (choice.firstPaymentDate().isBefore(row.spendingDate()))
                    throw new IllegalArgumentException("첫 청구일은 승인일보다 빠를 수 없습니다.");
                row.installment().payments(choice.firstPaymentDate());
                scheduledCount = Math.addExact(scheduledCount, row.installment().months());
                if (scheduledCount > MAX_ROWS) throw new IllegalArgumentException("예정 회차는 한 번에 최대 1,000건까지 생성할 수 있습니다.");
                if (recurringRepository == null) throw new IllegalStateException("할부 예정 저장소가 설정되지 않았습니다.");
            }
            if (!row.duplicateCandidate() && currentMatches.containsKey(
                    SpendingDuplicateSupport.key(row.spendingDate(), row.amount(), row.merchantName())))
                throw new IllegalArgumentException("미리보기 이후 같은 내역이 등록됐습니다. 파일을 다시 올려 중복 후보를 확인해 주세요.");
            String category = categoryByIndex.get(index);
            if (category == null || category.isBlank()) merchantsForPrediction.add(row.merchantName());
            else if (!categories.contains(category))
                throw new IllegalArgumentException(row.sourceRow() + "행의 카테고리가 올바르지 않습니다.");
        }
        Map<String, String> predictedCategories = categoryPredictionService
                .predictByMerchant(merchantsForPrediction, activeCategories);

        List<DailySpending> entities = new ArrayList<>();
        List<RecurringExpense> schedules = new ArrayList<>();
        int duplicates = 0;
        Set<String> transactionKeys = new HashSet<>();
        for (Integer index : uniqueIndexes) {
            CardSpendingImportRow row = preview.rows().get(index);
            String category = categoryByIndex.get(index);
            if (category == null || category.isBlank()) category = predictedCategories.get(row.merchantName());
            if (!categories.contains(category)) throw new IllegalArgumentException("사용 가능한 지출 카테고리를 선택해 주세요.");
            if (!transactionKeys.add(row.transactionKey())
                    || (row.installment() != null && recurringRepository.existsByMemberMemberIdAndCardInstallmentKey(
                            member.getMemberId(), installmentKey(row.transactionKey(), 1)))
                    || repository.existsByMemberMemberIdAndImportSourceAndImportTransactionKey(
                    member.getMemberId(), preview.cardCompany().name(), row.transactionKey())) {
                duplicates++;
                continue;
            }
            if (row.installment() != null) {
                LocalDate firstDate = installments.get(index).firstPaymentDate();
                for (var payment : row.installment().payments(firstDate)) {
                    String name = "[할부 예상 " + payment.round() + "/" + row.installment().months() + "] " + row.merchantName();
                    schedules.add(RecurringExpense.builder().member(member)
                            .name(name.substring(0, Math.min(name.length(), 100)))
                            .categoryCode(category).amount(payment.amount()).paymentDay(firstDate.getDayOfMonth())
                            .startDate(YearMonth.from(payment.date()).atDay(1))
                            .endDate(YearMonth.from(payment.date()).atEndOfMonth()).active(true)
                            .memo("승인일 " + row.spendingDate() + " · 총액 " + row.amount()
                                    + "원 균등분할 예상 원금. 수수료·할인 미포함; 실제 청구액 확인 후 확정")
                            .cardInstallmentKey(installmentKey(row.transactionKey(), payment.round())).build());
                }
                continue;
            }
            entities.add(DailySpending.builder()
                    .member(member)
                    .spendingDate(row.spendingDate())
                    .categoryCode(category)
                    .amount(row.amount())
                    .memo(row.merchantName())
                    .importSource(preview.cardCompany().name())
                    .importTransactionKey(row.transactionKey())
                    .build());
        }
        try {
            repository.saveAll(entities);
            repository.flush();
            if (!schedules.isEmpty()) {
                recurringRepository.saveAll(schedules);
                recurringRepository.flush();
            }
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException("이미 등록된 카드 이용내역이 포함되어 있습니다.", e);
        }
        return new ImportResult(entities.size(), duplicates, schedules.size());
    }

    private List<DailySpending> existing(List<CardSpendingImportRow> rows, Member member) {
        LocalDate start = rows.stream().map(CardSpendingImportRow::spendingDate).min(LocalDate::compareTo).orElseThrow();
        LocalDate end = rows.stream().map(CardSpendingImportRow::spendingDate).max(LocalDate::compareTo).orElseThrow();
        return repository.findByMemberMemberIdAndSpendingDateBetween(member.getMemberId(), start, end);
    }

    private List<CardSpendingImportRow> markDuplicates(CardCompany company, List<CardSpendingImportRow> rows, Member member) {
        var existing = existing(rows, member);
        var index = SpendingDuplicateSupport.index(existing);
        Set<String> importedKeys = new HashSet<>();
        for (DailySpending row : existing) {
            if (company.name().equals(row.getImportSource())) importedKeys.add(row.getImportTransactionKey());
        }
        Set<SpendingDuplicateSupport.Key> seen = new HashSet<>();
        for (DailySpending imported : repository.findByMemberMemberIdAndImportSourceAndImportTransactionKeyIn(
                member.getMemberId(), company.name(), rows.stream().map(row -> row.transactionKey()).toList()))
            importedKeys.add(imported.getImportTransactionKey());
        List<CardSpendingImportRow> result = new ArrayList<>();
        for (CardSpendingImportRow row : rows) {
            var key = SpendingDuplicateSupport.key(row.spendingDate(), row.amount(), row.merchantName());
            boolean repeated = row.importable() && !seen.add(key);
            result.add(new CardSpendingImportRow(row.sourceRow(), row.spendingDate(), row.merchantName(), row.amount(),
                    row.transactionKey(), row.errorMessage(), row.sourceSheet(),
                    SpendingDuplicateSupport.matches(index.getOrDefault(key, List.of())), repeated,
                    importedKeys.contains(row.transactionKey()) || (row.installment() != null && recurringRepository != null
                            && recurringRepository.existsByMemberMemberIdAndCardInstallmentKey(member.getMemberId(),
                                    installmentKey(row.transactionKey(), 1))), row.installment()));
        }
        return List.copyOf(result);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("업로드할 엑셀 파일을 선택해 주세요.");
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase(java.util.Locale.ROOT).endsWith(".xlsx"))
            throw new IllegalArgumentException("XLSX 파일만 업로드할 수 있습니다.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("엑셀 파일은 5MB 이하여야 합니다.");
    }

    private String safeFilename(String filename) {
        if (filename == null) return "카드 이용내역.xlsx";
        return java.nio.file.Path.of(filename).getFileName().toString();
    }

    static String installmentKey(String transactionKey, int round) {
        return CardStatementParserSupport.transactionKey("CARD_INSTALLMENT", transactionKey,
                Integer.toString(round), 0, "");
    }

    public record ImportResult(int savedCount, int duplicateCount, int scheduledCount) {
        public ImportResult(int savedCount, int duplicateCount) { this(savedCount, duplicateCount, 0); }
    }
}
