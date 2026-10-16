package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardImportPreview;
import com.deveopsj.spending.dto.CardSpendingImportRow;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

class CardSpendingImportServiceTest {

    @Test
    void 삼성_명세서의_두시트와_청구월을_미리보기에_반영한다() throws Exception {
        CardSpendingImportService service = new CardSpendingImportService(
                mock(DailySpendingRepository.class), mock(MasterCodeService.class),
                mock(SpendingCategoryPredictionService.class), List.of(new SamsungCardStatementParser()));
        byte[] contents;
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            String[] headers = {"이용일", "이용구분", "가맹점", "이용금액", "개월", "회차", "원금", "이자/수수료"};
            for (String name : List.of("일시불", "할부")) {
                var sheet = workbook.createSheet(name);
                var header = sheet.createRow(2);
                for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
                var data = sheet.createRow(3);
                String[] values = {"20260703", "본인 123", "테스트", "100000", "2", "1", "50000", "1200"};
                for (int i = 0; i < values.length; i++) data.createCell(i).setCellValue(values[i]);
            }
            workbook.write(output);
            contents = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "samsung.xlsx", null, contents);
        var preview = service.preview(CardCompany.SAMSUNG, file, null, java.time.YearMonth.of(2026, 8));
        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows().get(1).amount()).isEqualTo(51_200);
        assertThat(preview.rows().get(1).spendingDate()).hasToString("2026-08-01");
        assertThat(preview.notice()).contains("2026-08", "원금+이자/수수료");
        assertThatThrownBy(() -> service.preview(CardCompany.SAMSUNG, file))
                .hasMessageContaining("청구월");
    }

    @Test
    void 암호화된_카카오뱅크_엑셀을_입력한_비밀번호로_미리보기한다() throws Exception {
        CardSpendingImportService service = new CardSpendingImportService(
                mock(DailySpendingRepository.class), mock(MasterCodeService.class),
                mock(SpendingCategoryPredictionService.class), List.of(new KakaoBankCardStatementParser()));
        MockMultipartFile file = new MockMultipartFile("file", "kakao.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                encryptedKakaoWorkbook("fixture-password"));

        CardImportPreview preview = service.preview(CardCompany.KAKAO_BANK, file, "fixture-password");

        assertThat(preview.rows()).singleElement().satisfies(row -> {
            assertThat(row.sourceRow()).isEqualTo(18);
            assertThat(row.amount()).isEqualTo(10_000L);
            assertThat(row.importable()).isTrue();
        });
    }

    @Test
    void 암호화된_엑셀의_비밀번호_누락과_오입력을_구분해_안내한다() throws Exception {
        CardSpendingImportService service = new CardSpendingImportService(
                mock(DailySpendingRepository.class), mock(MasterCodeService.class),
                mock(SpendingCategoryPredictionService.class), List.of(new KakaoBankCardStatementParser()));
        byte[] contents = encryptedKakaoWorkbook("fixture-password");
        MockMultipartFile file = new MockMultipartFile("file", "kakao.xlsx", null, contents);

        assertThatThrownBy(() -> service.preview(CardCompany.KAKAO_BANK, file, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("비밀번호를 입력");
        assertThatThrownBy(() -> service.preview(CardCompany.KAKAO_BANK, file, "wrong-password"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("올바르지 않습니다");
    }

    @Test
    void 선택한_카테고리와_인증사용자로_카드지출을_저장한다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        CardSpendingImportService service = service(repository, codes);
        Member member = new Member(); member.setMemberId(7L);
        CardImportPreview preview = preview();

        var result = service.confirm(preview, List.of(0), Map.of(0, "FOOD"), member);

        assertThat(result.savedCount()).isEqualTo(1);
        ArgumentCaptor<List<DailySpending>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(spending -> {
            assertThat(spending.getMember()).isSameAs(member);
            assertThat(spending.getImportSource()).isEqualTo("SHINHAN");
            assertThat(spending.getCategoryCode()).isEqualTo("FOOD");
        });
    }

    @Test
    void 같은사용자의_중복거래는_저장하지않는다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").build()));
        when(repository.existsByMemberMemberIdAndImportSourceAndImportTransactionKey(7L, "SHINHAN", "key"))
                .thenReturn(true);
        CardSpendingImportService service = service(repository, codes);
        Member member = new Member(); member.setMemberId(7L);

        var result = service.confirm(preview(), List.of(0), Map.of(0, "FOOD"), member);

        assertThat(result.savedCount()).isZero();
        assertThat(result.duplicateCount()).isEqualTo(1);
        verify(repository).saveAll(List.of());
    }

    @Test
    void 같은파일안의_중복거래도_한건만_저장한다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").build()));
        CardSpendingImportService service = service(repository, codes);
        Member member = new Member(); member.setMemberId(7L);
        CardSpendingImportRow row = preview().rows().get(0);
        CardImportPreview duplicated = new CardImportPreview("token", CardCompany.SHINHAN,
                "sample.xlsx", List.of(row, row), null, 7L);

        var result = service.confirm(duplicated, List.of(0, 1), Map.of(0, "FOOD", 1, "FOOD"), member);

        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isEqualTo(1);
    }

    @Test
    void 비활성_카테고리나_조작된_행번호를_거부한다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").build()));
        CardSpendingImportService service = service(repository, codes);
        Member member = new Member(); member.setMemberId(7L);

        assertThatThrownBy(() -> service.confirm(preview(), List.of(99), Map.of(), member))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("행");
        assertThatThrownBy(() -> service.confirm(preview(), List.of(0), Map.of(0, "FAKE"), member))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("카테고리");
    }

    @Test
    void 활성_카테고리가_없으면_저장하지않는다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of());
        CardSpendingImportService service = service(repository, codes);
        Member member = new Member(); member.setMemberId(7L);

        assertThatThrownBy(() -> service.confirm(preview(), List.of(0), Map.of(), member))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("카테고리");
    }

    @Test
    void 빈_카테고리는_AI_예측값으로_저장한다() {
        DailySpendingRepository repository = mock(DailySpendingRepository.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        SpendingCategoryPredictionService prediction = mock(SpendingCategoryPredictionService.class);
        when(prediction.predictByMerchant(any(), any())).thenReturn(Map.of("테스트", "FOOD"));
        CardSpendingImportService service = new CardSpendingImportService(
                repository, codes, prediction, List.of());
        Member member = new Member(); member.setMemberId(7L);

        service.confirm(preview(), List.of(0), Map.of(0, ""), member);

        ArgumentCaptor<List<DailySpending>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getCategoryCode()).isEqualTo("FOOD");
        verify(prediction).predictByMerchant(eq(Set.of("테스트")), anyList());
    }

@Test
    void 카드_미리보기에서_기존_수동지출을_중복후보로_보여준다() throws Exception {
        var repository = mock(DailySpendingRepository.class);
        var old = DailySpending.builder().id(99L).spendingDate(LocalDate.of(2026, 8, 29))
                .amount(10_000L).memo("테스트").categoryCode("FOOD").build();
        old.setCreateDate(java.time.LocalDateTime.of(2026, 8, 30, 10, 0));
        when(repository.findByMemberMemberIdAndSpendingDateBetween(eq(7L), any(), any())).thenReturn(List.of(old));
        var service = previewService(repository, List.of(preview().rows().get(0)));
        Member member = new Member(); member.setMemberId(7L);

        var result = service.preview(CardCompany.SHINHAN, plainCardFile(), null, null, member);

        assertThat(result.memberId()).isEqualTo(7L);
        assertThat(result.rows().get(0).matches()).singleElement().satisfies(match -> assertThat(match.id()).isEqualTo(99L));
        assertThat(result.rows().get(0).importable()).isTrue();
        assertThat(result.rows().get(0).selectedByDefault()).isFalse();
        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    @Test
    void 카드의_확정중복은_선택을_막는다() throws Exception {
        var repository = mock(DailySpendingRepository.class);
        var old = DailySpending.builder().spendingDate(LocalDate.of(2026, 8, 29)).amount(10_000L)
                .memo("테스트").categoryCode("FOOD").importSource("SHINHAN").importTransactionKey("key").build();
        when(repository.findByMemberMemberIdAndSpendingDateBetween(eq(7L), any(), any())).thenReturn(List.of(old));
        var service = previewService(repository, List.of(preview().rows().get(0)));
        Member member = new Member(); member.setMemberId(7L);
        var result = service.preview(CardCompany.SHINHAN, plainCardFile(), null, null, member);
        assertThat(result.rows().get(0).alreadyImported()).isTrue();
        assertThat(result.rows().get(0).importable()).isFalse();
    }

    @Test
    void 서로다른_카드거래의_내용이같으면_두번째만_중복후보로_표시한다() throws Exception {
        var repository = mock(DailySpendingRepository.class);
        var first = preview().rows().get(0);
        var second = new CardSpendingImportRow(3, first.spendingDate(), first.merchantName(), first.amount(), "other-key", null);
        var service = previewService(repository, List.of(first, second));
        Member member = new Member(); member.setMemberId(7L);
        var result = service.preview(CardCompany.SHINHAN, plainCardFile(), null, null, member);
        assertThat(result.rows().get(0).selectedByDefault()).isTrue();
        assertThat(result.rows().get(1).duplicateInFile()).isTrue();
        assertThat(result.rows().get(1).selectedByDefault()).isFalse();
        assertThat(result.rows().get(1).importable()).isTrue();
    }

    @Test
    void 카드_미리보기_소유자가_다르면_등록하지않는다() {
        var repository = mock(DailySpendingRepository.class);
        var service = service(repository, mock(MasterCodeService.class));
        Member other = new Member(); other.setMemberId(8L);
        assertThatThrownBy(() -> service.confirm(preview(), List.of(0), Map.of(0, "FOOD"), other))
                .hasMessageContaining("본인의");
        verify(repository, org.mockito.Mockito.never()).saveAll(any());
    }

    private CardSpendingImportService previewService(DailySpendingRepository repository, List<CardSpendingImportRow> rows) {
        CardStatementParser parser = new CardStatementParser() {
            public CardCompany cardCompany() { return CardCompany.SHINHAN; }
            public List<CardSpendingImportRow> parse(org.apache.poi.ss.usermodel.Sheet sheet) { return rows; }
        };
        return new CardSpendingImportService(repository, mock(MasterCodeService.class),
                mock(SpendingCategoryPredictionService.class), List.of(parser));
    }

    private MockMultipartFile plainCardFile() throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            workbook.createSheet();
            workbook.write(output);
            return new MockMultipartFile("file", "sample.xlsx", null, output.toByteArray());
        }
    }

    private CardImportPreview preview() {
        return new CardImportPreview("token", CardCompany.SHINHAN, "sample.xlsx", List.of(
                new CardSpendingImportRow(2, LocalDate.of(2026, 8, 29), "테스트", 10_000L, "key", null)), null, 7L);
    }

    private CardSpendingImportService service(DailySpendingRepository repository, MasterCodeService codes) {
        SpendingCategoryPredictionService prediction = mock(SpendingCategoryPredictionService.class);
        when(prediction.predictByMerchant(any(), any())).thenReturn(Map.of());
        return new CardSpendingImportService(repository, codes, prediction, List.of());
    }

    private byte[] encryptedKakaoWorkbook(String password) throws Exception {
        byte[] plainWorkbook;
        try (XSSFWorkbook workbook = new XSSFWorkbook();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("카카오뱅크 카드이용내역");
            var header = sheet.createRow(16);
            String[] headers = {"거래일시", "카드번호\n(뒷자리)", "취소여부", "매출구분",
                    "매출금액", "부가세액", "가맹점명", "사업자등록번호", "과세유형"};
            for (int i = 0; i < headers.length; i++) header.createCell(i + 1).setCellValue(headers[i]);
            var data = sheet.createRow(17);
            String[] values = {"2026.08.07 12:34:56", "1234", "정상", "일시불",
                    "10000", "909", "테스트 가맹점", "1234567890", "일반과세자"};
            for (int i = 0; i < values.length; i++) data.createCell(i + 1).setCellValue(values[i]);
            workbook.write(output);
            plainWorkbook = output.toByteArray();
        }

        try (POIFSFileSystem fileSystem = new POIFSFileSystem();
                ByteArrayOutputStream encryptedOutput = new ByteArrayOutputStream()) {
            EncryptionInfo encryptionInfo = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = encryptionInfo.getEncryptor();
            encryptor.confirmPassword(password);
            try (var encryptedStream = encryptor.getDataStream(fileSystem)) {
                encryptedStream.write(plainWorkbook);
            }
            fileSystem.writeFilesystem(encryptedOutput);
            return encryptedOutput.toByteArray();
        }
    }
}
