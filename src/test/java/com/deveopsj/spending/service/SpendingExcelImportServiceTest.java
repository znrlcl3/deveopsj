package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class SpendingExcelImportServiceTest {

    @Mock DailySpendingRepository dailySpendingRepository;
    @Mock MasterCodeService masterCodeService;

    @Test
    void 다운로드형식의_엑셀을_인증사용자_지출로_등록한다() throws Exception {
        SpendingExcelImportService service = service();
        Member member = member(7L);
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        MockMultipartFile file = file(false,
                new String[]{LocalDate.now().minusDays(1).toString(), "식비", "12500", "점심", "일반"});

        int count = service.importFile(file, member);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<List<DailySpending>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailySpendingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(spending -> {
            assertThat(spending.getMember()).isSameAs(member);
            assertThat(spending.getCategoryCode()).isEqualTo("FOOD");
            assertThat(spending.getAmount()).isEqualTo(12_500L);
            assertThat(spending.getRecurringExpense()).isNull();
        });
    }

    @Test
    void 한행이라도_잘못되면_아무것도_저장하지않는다() throws Exception {
        SpendingExcelImportService service = service();
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        MockMultipartFile file = file(false,
                new String[]{LocalDate.now().minusDays(2).toString(), "식비", "10000", "정상"},
                new String[]{LocalDate.now().minusDays(1).toString(), "없는분류", "20000", "오류"});

        assertThatThrownBy(() -> service.importFile(file, member(7L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3행");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

    @Test
    void 수식셀이_있으면_업로드를_거부한다() throws Exception {
        SpendingExcelImportService service = service();
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        MockMultipartFile file = file(true,
                new String[]{LocalDate.now().minusDays(1).toString(), "식비", "10000", "메모"});

        assertThatThrownBy(() -> service.importFile(file, member(7L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("수식 셀");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

@Test
    void 미리보기에서_기존_내역과_파일내_중복을_표시하고_저장하지않는다() throws Exception {
        stubCategories();
        LocalDate date = LocalDate.now().minusDays(1);
        DailySpending old = DailySpending.builder().id(99L).spendingDate(date).amount(10000L)
                .categoryCode("OTHER").memo("  같은   메모 ").build();
        old.setCreateDate(java.time.LocalDateTime.of(2026, 8, 1, 12, 30));
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, date, date))
                .thenReturn(List.of(old));
        var file = file(false, new String[]{date.toString(), "식비", "10000", "같은 메모"},
                new String[]{date.toString(), "FOOD", "10000", "같은 메모"});
        var preview = service().preview(file, member(7L), "2026-08");

        assertThat(preview.rows()).hasSize(2);
        assertThat(preview.rows().get(0).matches()).singleElement().satisfies(match -> {
            assertThat(match.id()).isEqualTo(99L);
            assertThat(match.createDate()).isEqualTo(old.getCreateDate());
        });
        assertThat(preview.rows().get(1).duplicateInFile()).isTrue();
        assertThat(preview.rows()).noneMatch(row -> row.selectedByDefault());
        assertThat(preview.rows().get(0).transactionKey()).isNotEqualTo(preview.rows().get(1).transactionKey());
        verify(dailySpendingRepository, never()).saveAll(any());
        verify(dailySpendingRepository).findByMemberMemberIdAndSpendingDateBetween(7L, date, date);
    }

    @Test
    void 사용자가_확인한_중복후보만_선택해_등록할수있다() throws Exception {
        stubCategories();
        LocalDate date = LocalDate.now().minusDays(1);
        DailySpending old = DailySpending.builder().id(99L).spendingDate(date).amount(10000L)
                .categoryCode("FOOD").memo("점심").build();
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, date, date)).thenReturn(List.of(old));
        var service = service();
        var member = member(7L);
        var preview = service.preview(file(false, new String[]{date.toString(), "식비", "10000", "점심"}), member, null);
        assertThat(preview.rows().get(0).selectedByDefault()).isFalse();
        assertThat(service.confirm(preview, List.of(0), member)).isEqualTo(1);
        ArgumentCaptor<List<DailySpending>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailySpendingRepository).saveAll(captor.capture());
        assertThat(captor.getValue().get(0).getImportSource()).isEqualTo("EXCEL");
        assertThat(captor.getValue().get(0).getImportTransactionKey()).hasSize(64);
    }

    @Test
    void 동일파일에서_이미등록한_행은_다시_등록할수없다() throws Exception {
        stubCategories();
        LocalDate date = LocalDate.now().minusDays(1);
        var file = file(false, new String[]{date.toString(), "식비", "10000", "점심"});
        var service = service();
        var member = member(7L);
        var first = service.preview(file, member, null);
        DailySpending old = DailySpending.builder().id(99L).spendingDate(date).amount(10000L)
                .categoryCode("FOOD").memo("점심").importSource("EXCEL")
                .importTransactionKey(first.rows().get(0).transactionKey()).build();
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, date, date)).thenReturn(List.of(old));
        var repeated = service.preview(file, member, null);
        assertThat(repeated.rows().get(0).alreadyImported()).isTrue();
        assertThatThrownBy(() -> service.confirm(repeated, List.of(0), member)).hasMessageContaining("이미 등록");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

    @Test
    void 미리보기후_새중복이_생기면_재확인을_요청한다() throws Exception {
        stubCategories();
        LocalDate date = LocalDate.now().minusDays(1);
        var service = service();
        var member = member(7L);
        var preview = service.preview(file(false, new String[]{date.toString(), "식비", "10000", "점심"}), member, null);
        var old = DailySpending.builder().spendingDate(date).amount(10000L).memo("점심").build();
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(7L, date, date)).thenReturn(List.of(old));
        assertThatThrownBy(() -> service.confirm(preview, List.of(0), member)).hasMessageContaining("미리보기 이후");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

    @Test
    void 타인의_미리보기와_조작된_행번호는_저장하지않는다() throws Exception {
        stubCategories();
        var service = service();
        var owner = member(7L);
        var preview = service.preview(file(false, new String[]{LocalDate.now().minusDays(1).toString(), "식비", "10000", "점심"}), owner, null);
        assertThatThrownBy(() -> service.confirm(preview, List.of(0), member(8L))).hasMessageContaining("만료");
        assertThatThrownBy(() -> service.confirm(preview, List.of(999), owner)).hasMessageContaining("행");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

    @Test
    void 동시_파일등록_충돌은_사용자에게_재확인을_안내한다() throws Exception {
        stubCategories();
        var service = service();
        var member = member(7L);
        var preview = service.preview(file(false, new String[]{LocalDate.now().minusDays(1).toString(), "식비", "10000", "점심"}), member, null);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"))
                .when(dailySpendingRepository).flush();
        assertThatThrownBy(() -> service.confirm(preview, List.of(0), member)).hasMessageContaining("이미 등록");
    }

@Test
    void 등록한_행의_날짜를_수정해도_동일파일_재등록을_차단한다() throws Exception {
        stubCategories();
        var service = service();
        var owner = member(7L);
        var file = file(false, new String[]{LocalDate.now().minusDays(1).toString(), "식비", "10000", "점심"});
        var first = service.preview(file, owner, null);
        var modified = DailySpending.builder().spendingDate(LocalDate.now().minusDays(20)).amount(10000L)
                .memo("수정").importSource("EXCEL").importTransactionKey(first.rows().get(0).transactionKey()).build();
        when(dailySpendingRepository.findByMemberMemberIdAndImportSourceAndImportTransactionKeyIn(
                org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.eq("EXCEL"), any()))
                .thenReturn(List.of(modified));
        var repeated = service.preview(file, owner, null);
        assertThat(repeated.rows().get(0).alreadyImported()).isTrue();
        assertThatThrownBy(() -> service.confirm(first, List.of(0), owner)).hasMessageContaining("이미 등록");
        verify(dailySpendingRepository, never()).saveAll(any());
    }

    private void stubCategories() {
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
    }

    private SpendingExcelImportService service() {
        return new SpendingExcelImportService(dailySpendingRepository, masterCodeService);
    }

    private MockMultipartFile file(boolean formulaMemo, String[]... rows) throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("지출 내역");
            var header = sheet.createRow(0);
            String[] headers = {"날짜", "카테고리", "금액(원)", "메모", "고정지출"};
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            for (int i = 0; i < rows.length; i++) {
                var row = sheet.createRow(i + 1);
                for (int j = 0; j < rows[i].length; j++) row.createCell(j).setCellValue(rows[i][j]);
                if (formulaMemo) row.getCell(3).setCellFormula("1+1");
            }
            workbook.write(output);
            return new MockMultipartFile("file", "spending.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", output.toByteArray());
        }
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }
}
