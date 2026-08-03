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
