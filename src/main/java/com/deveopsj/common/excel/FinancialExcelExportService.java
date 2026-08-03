package com.deveopsj.common.excel;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.income.entity.Income;
import com.deveopsj.income.service.IncomeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.service.SpendingService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FinancialExcelExportService {

    private final IncomeService incomeService;
    private final SpendingService spendingService;
    private final AssetTradeService assetTradeService;
    private final AssetSavingsService assetSavingsService;
    private final MasterCodeService masterCodeService;

    public byte[] incomes(Member member, ExcelExportRange range) {
        List<Income> rows = incomeService.getIncomesByMemberAndPeriod(
                member, range.startDate(), range.endDate());
        return workbook("수입 내역", List.of("날짜", "수입 유형", "금액(원)", "메모"), sheet -> {
            int rowIndex = 1;
            for (Income income : rows) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, income.getIncomeDate());
                text(row, 1, income.getIncomeType().getLabel());
                number(row, 2, income.getAmount());
                text(row, 3, income.getMemo());
            }
        });
    }

    public byte[] spendings(Member member, ExcelExportRange range) {
        List<DailySpending> rows = spendingService.getSpendings(
                member, range.startDate(), range.endDate());
        return workbook("지출 내역", List.of("날짜", "카테고리", "금액(원)", "메모", "고정지출"), sheet -> {
            int rowIndex = 1;
            for (DailySpending spending : rows) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, spending.getSpendingDate());
                text(row, 1, masterCodeService.getCodeName("SPENDING_CAT", spending.getCategoryCode()));
                number(row, 2, spending.getAmount());
                text(row, 3, spending.getMemo());
                text(row, 4, spending.getRecurringExpense() == null ? "일반" : "고정지출");
            }
        });
    }

    public byte[] trades(Member member, ExcelExportRange range) {
        List<AssetTrade> rows = assetTradeService.getTradesByMemberAndPeriod(
                member, range.startDate(), range.endDate());
        return workbook("주식 ETF 매매", List.of("거래일", "종목", "종목코드", "시장", "유형", "수량",
                "체결단가", "총 매매금액", "통화", "환율", "수수료(원)", "세금(원)", "원화 정산금액", "메모"), sheet -> {
            int rowIndex = 1;
            for (AssetTrade trade : rows) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, trade.getTradeDate());
                text(row, 1, trade.getInvestmentAsset().getAssetName());
                text(row, 2, trade.getInvestmentAsset().getSymbol());
                text(row, 3, trade.getInvestmentAsset().getMarket());
                text(row, 4, trade.getTradeType().name().equals("BUY") ? "매수" : "매도");
                decimal(row, 5, trade.getQuantity());
                decimal(row, 6, trade.getUnitPrice());
                decimal(row, 7, trade.getQuantity().multiply(trade.getUnitPrice()));
                text(row, 8, trade.getCurrency());
                decimal(row, 9, trade.getExchangeRate());
                number(row, 10, trade.getFeeKrw());
                number(row, 11, trade.getTaxKrw());
                number(row, 12, trade.getSettlementAmountKrw());
                text(row, 13, trade.getMemo());
            }
        });
    }

    public byte[] savings(Member member, ExcelExportRange range) {
        List<AssetSavings> rows = assetSavingsService.getSavingsByMemberAndPeriod(
                member, range.startDate(), range.endDate());
        return workbook("납입 내역", List.of("납입일", "납입 유형", "대상", "금액(원)", "메모"), sheet -> {
            int rowIndex = 1;
            for (AssetSavings saving : rows) {
                Row row = sheet.createRow(rowIndex++);
                date(row, 0, saving.getDepositDate());
                text(row, 1, saving.getDepositType() == AssetSavings.DepositType.PLAN ? "정기 플랜" : "추가 납입");
                text(row, 2, savingsTarget(saving));
                number(row, 3, saving.getAmount());
                text(row, 4, saving.getMemo());
            }
        });
    }

    private String savingsTarget(AssetSavings saving) {
        if (saving.getAssetPlan() == null) {
            return saving.getGoal() == null ? "" : saving.getGoal().getTitle();
        }
        AssetPlan plan = saving.getAssetPlan();
        return plan.getPlanName() == null || plan.getPlanName().isBlank()
                ? plan.getGoal().getTitle() : plan.getPlanName();
    }

    private byte[] workbook(String sheetName, List<String> headers, SheetWriter writer) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);
            Row header = sheet.createRow(0);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            writer.write(sheet);
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, sheet.getLastRowNum()), 0, headers.size() - 1));
            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
                sheet.setColumnWidth(i, Math.min(sheet.getColumnWidth(i) + 700, 15000));
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("엑셀 파일을 생성하지 못했습니다.", e);
        }
    }

    private void date(Row row, int column, LocalDate value) {
        row.createCell(column).setCellValue(value == null ? "" : value.toString());
    }

    private void number(Row row, int column, Long value) {
        row.createCell(column).setCellValue(value == null ? "" : value.toString());
    }

    private void decimal(Row row, int column, BigDecimal value) {
        row.createCell(column).setCellValue(value == null
                ? "" : value.stripTrailingZeros().toPlainString());
    }

    private void text(Row row, int column, String value) {
        row.createCell(column).setCellValue(safeText(value));
    }

    static String safeText(String value) {
        if (value == null) return "";
        String trimmed = value.stripLeading();
        return !trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0 ? "'" + value : value;
    }

    @FunctionalInterface
    private interface SheetWriter {
        void write(Sheet sheet);
    }
}
