package com.deveopsj.common.excel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.income.entity.Income;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.income.service.IncomeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.service.SpendingService;

class FinancialExcelExportServiceTest {

    @Test
    void 수식으로_해석될수있는_문자열을_일반문자열로_변환한다() {
        assertThat(FinancialExcelExportService.safeText("=1+1")).isEqualTo("'=1+1");
        assertThat(FinancialExcelExportService.safeText(" @SUM(A1:A2)"))
                .isEqualTo("' @SUM(A1:A2)");
        assertThat(FinancialExcelExportService.safeText("정상 메모")).isEqualTo("정상 메모");
    }

    @Test
    void 수입내역의_모든_데이터를_텍스트형식의_xlsx로_생성한다() throws Exception {
        IncomeService incomeService = mock(IncomeService.class);
        Member member = new Member();
        member.setMemberId(7L);
        ExcelExportRange range = new ExcelExportRange(
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        Income income = new Income();
        income.setIncomeDate(LocalDate.of(2026, 8, 3));
        income.setIncomeType(IncomeType.SALARY);
        income.setAmount(3_000_000L);
        income.setMemo("=1+1");
        when(incomeService.getIncomesByMemberAndPeriod(member, range.startDate(), range.endDate()))
                .thenReturn(List.of(income));
        FinancialExcelExportService service = new FinancialExcelExportService(
                incomeService, mock(SpendingService.class), mock(AssetTradeService.class),
                mock(AssetSavingsService.class), mock(MasterCodeService.class));

        byte[] result = service.incomes(member, range);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(result))) {
            var row = workbook.getSheetAt(0).getRow(1);
            for (var cell : row) {
                assertThat(cell.getCellType()).isEqualTo(CellType.STRING);
            }
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("2026-08-03");
            assertThat(row.getCell(2).getStringCellValue()).isEqualTo("3000000");
            assertThat(row.getCell(3).getStringCellValue()).isEqualTo("'=1+1");
        }
    }
}
