package com.deveopsj.spending.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.YearMonth;
import java.math.BigDecimal;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Component;

import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardSpendingImportRow;

@Component
public class SamsungCardStatementParser implements CardStatementParser {
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    @Override
    public CardCompany cardCompany() { return CardCompany.SAMSUNG; }

    @Override
    public List<CardSpendingImportRow> parse(Workbook workbook, YearMonth billingMonth) {
        List<CardSpendingImportRow> usageRows = new ArrayList<>();
        boolean usageFormat = false;
        for (Sheet sheet : workbook) {
            Row header = findUsageHeader(sheet);
            if (header != null) {
                usageFormat = true;
                usageRows.addAll(parseUsage(sheet, header));
            }
        }
        if (usageFormat) return usageRows;
        List<Sheet> statements = new ArrayList<>();
        for (Sheet sheet : workbook) {
            if (findStatementHeader(sheet) != null) statements.add(sheet);
        }
        if (statements.isEmpty()) return parse(workbook.getSheetAt(0));
        List<CardSpendingImportRow> result = new ArrayList<>();
        Map<String, Integer> occurrences = new HashMap<>();
        for (Sheet sheet : statements) {
            String type = sheet.getSheetName().replaceAll("\\s+", "");
            if (!"일시불".equals(type) && !"할부".equals(type)) {
                throw new IllegalArgumentException("삼성 청구명세서의 시트 이름은 일시불 또는 할부여야 합니다.");
            }
            parseStatement(sheet, "할부".equals(type), billingMonth, occurrences, result);
        }
        return result;
    }

    @Override
    public String previewNotice(Workbook workbook, YearMonth billingMonth) {
        for (Sheet sheet : workbook) {
            if (findUsageHeader(sheet) != null) {
                return "삼성카드 일시불+할부 카드이용내역조회: 상세 시트의 승인일자·승인금액으로 일시불을 등록합니다. "
                        + "결제일과 선택한 청구월은 일시불 지출일에 사용하지 않습니다. "
                        + "취소 거래는 등록하지 않으며 이미 저장된 지출을 자동으로 취소하지 않습니다. "
                        + "할부는 승인총액을 개월 수로 나눈 예상 원금이며 수수료·할인은 포함하지 않습니다. "
                        + "첫 청구일과 회차별 예상액을 확인하고 선택해야 예정 지출이 생성됩니다. 실제 지출은 월별 확정 시 반영됩니다. "
                        + "이미 청구명세서로 등록한 할부는 다시 선택하지 마세요.";
            }
        }
        for (Sheet sheet : workbook) {
            if (findStatementHeader(sheet) != null) {
                return "삼성 청구명세서: 일시불은 이용일·이용금액으로 등록합니다. "
                        + (billingMonth == null ? "할부가 있으면 청구월을 선택해야 합니다. "
                                : "할부는 청구월 " + billingMonth + "의 1일·원금+이자/수수료로 등록합니다. ")
                        + "할인·포인트 안내행은 제외합니다. "
                        + "승인번호가 없는 양식이므로 재업로드는 같은 명세서를 사용하고 중복 여부를 확인해 주세요.";
            }
        }
        return null;
    }

    private Row findUsageHeader(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 29); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            var headers = CardStatementParserSupport.headers(row);
            if (headers.containsKey("승인일자") && headers.containsKey("승인금액(원)")) return row;
        }
        return null;
    }

    private List<CardSpendingImportRow> parseUsage(Sheet sheet, Row header) {
        var h = CardStatementParserSupport.headers(header);
        CardStatementParserSupport.requireHeaders(h, "삼성카드 카드이용내역조회", "카드번호", "승인일자",
                "가맹점명", "승인금액(원)", "일시불할부구분", "할부개월", "승인번호", "취소여부");
        List<CardSpendingImportRow> rows = new ArrayList<>();
        java.util.Set<String> cancelled = new java.util.HashSet<>();
        for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (!CardStatementParserSupport.text(row, h.get("승인일자")).isBlank()
                    && isCancelled(CardStatementParserSupport.text(row, h.get("취소여부")))) {
                cancelled.add(usageApprovalIdentity(row, h));
            }
        }
        for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("승인일자"));
            if (dateText.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(dateText, DateTimeFormatter.ofPattern("uuuu.MM.dd")
                        .withResolverStyle(java.time.format.ResolverStyle.STRICT));
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                String card = CardStatementParserSupport.text(row, h.get("카드번호"));
                long amount = new BigDecimal(CardStatementParserSupport.text(row, h.get("승인금액(원)"))
                        .replace(",", "")).longValueExact();
                String product = CardStatementParserSupport.text(row, h.get("일시불할부구분"));
                String months = CardStatementParserSupport.text(row, h.get("할부개월"));
                com.deveopsj.spending.dto.CardInstallmentPlan installment = null;
                String error = null;
                if (isCancelled(CardStatementParserSupport.text(row, h.get("취소여부")))
                        || cancelled.contains(usageApprovalIdentity(row, h)))
                    error = "취소 또는 취소 이력이 있는 거래입니다. 기존 지출은 자동으로 삭제되지 않습니다.";
                else if ("할부".equals(product)) {
                    int count = new BigDecimal(months).intValueExact();
                    LocalDate firstDate = null;
                    String paymentDate = h.containsKey("결제일")
                            ? CardStatementParserSupport.text(row, h.get("결제일")) : "";
                    if (!paymentDate.isBlank()) {
                        try { firstDate = LocalDate.parse(paymentDate, DATE); }
                        catch (DateTimeParseException ignored) { /* User must supply a valid first billing date. */ }
                    }
                    installment = new com.deveopsj.spending.dto.CardInstallmentPlan(amount, count, firstDate);
                }
                else if (!"일시불".equals(product) || !(months.isBlank() || "0".equals(months)))
                    error = "지원하지 않는 이용구분 또는 할부개월입니다.";
                if (error == null) {
                    if (merchant.isBlank() || approval.isBlank() || card.isBlank())
                        error = "가맹점명, 승인번호 또는 카드 식별정보가 없습니다.";
                    else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                    else if (amount <= 0) error = "승인금액은 0보다 커야 합니다.";
                    else if (date.isAfter(LocalDate.now())) error = "미래 거래는 등록할 수 없습니다.";
                }
                // Keep the legacy approval-based key so overlapping business exports remain idempotent.
                String key = CardStatementParserSupport.transactionKey(installment == null ? "SAMSUNG" : "SAMSUNG_INSTALLMENT",
                        installment == null ? approval : card + ":" + approval,
                        date.toString(), amount, merchant);
                rows.add(new CardSpendingImportRow(i + 1, date, merchant, amount, key, error, sheet.getSheetName(),
                        List.of(), false, false, installment));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(sheet.getSheetName() + " " + (i + 1) + "행: 승인일자는 yyyy.MM.dd 형식이어야 합니다.");
            } catch (NumberFormatException | ArithmeticException e) {
                throw new IllegalArgumentException(sheet.getSheetName() + " " + (i + 1) + "행: 승인금액은 정수여야 합니다.");
            }
        }
        return rows;
    }

    private boolean isCancelled(String value) {
        return !(value.isBlank() || "-".equals(value) || "정상".equals(value));
    }

    private String usageApprovalIdentity(Row row, Map<String, Integer> h) {
        return String.join("|", CardStatementParserSupport.text(row, h.get("카드번호")),
                CardStatementParserSupport.text(row, h.get("승인번호")));
    }

    private Row findStatementHeader(Sheet sheet) {
        for (int i = 0; i <= Math.min(sheet.getLastRowNum(), 29); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Map<String, Integer> headers = CardStatementParserSupport.headers(row);
            if (headers.containsKey("이용일") && headers.containsKey("가맹점")
                    && headers.containsKey("이용금액")) return row;
        }
        return null;
    }

    private void parseStatement(Sheet sheet, boolean installment, YearMonth billingMonth,
            Map<String, Integer> occurrences, List<CardSpendingImportRow> result) {
        Row header = findStatementHeader(sheet);
        Map<String, Integer> h = CardStatementParserSupport.headers(header);
        CardStatementParserSupport.requireHeaders(h, "삼성 청구명세서", "이용일", "이용구분", "가맹점", "이용금액");
        if (installment) {
            CardStatementParserSupport.requireHeaders(h, "삼성 할부 청구명세서", "개월", "회차", "원금", "이자/수수료");
        }
        for (int i = header.getRowNum() + 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("이용일"));
            if (dateText.isBlank()) continue; // Discounts, points and summary lines are not transactions.
            try {
                LocalDate purchaseDate = LocalDate.parse(dateText, DATE);
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점"));
                String card = CardStatementParserSupport.text(row, h.get("이용구분"));
                long purchaseAmount = CardStatementParserSupport.positiveAmount(row, h.get("이용금액"));
                LocalDate spendingDate = purchaseDate;
                long amount = purchaseAmount;
                String round = "일시불";
                if (installment) {
                    if (billingMonth == null) throw new IllegalArgumentException("삼성 할부 명세서의 청구월을 선택해 주세요.");
                    int months = positiveInteger(row, h.get("개월"));
                    int currentRound = positiveInteger(row, h.get("회차"));
                    if (currentRound > months) throw new IllegalArgumentException("할부 회차가 개월 수를 초과합니다.");
                    long principal = nonNegativeAmount(row, h.get("원금"), false);
                    long fee = nonNegativeAmount(row, h.get("이자/수수료"), true);
                    amount = Math.addExact(principal, fee);
                    if (amount <= 0) throw new IllegalArgumentException("할부 청구액은 0보다 커야 합니다.");
                    spendingDate = billingMonth.atDay(1);
                    round = months + "/" + currentRound;
                }
                String error = null;
                if (merchant.isBlank() || card.isBlank()) error = "가맹점명 또는 카드 식별정보가 없습니다.";
                else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                else if (purchaseDate.isAfter(LocalDate.now()) || spendingDate.isAfter(LocalDate.now()))
                    error = "미래 거래 또는 미래 청구월은 등록할 수 없습니다.";
                else if (installment && billingMonth.isBefore(YearMonth.from(purchaseDate)))
                    error = "청구월은 이용일이 속한 월보다 빠를 수 없습니다.";

                // Invoice sheets have no approval number. Preserve distinct identical purchases by ordinal.
                // Instalment identity uses its round, so changing the selected month cannot duplicate that round.
                String baseKey = CardStatementParserSupport.transactionKey("SAMSUNG_STATEMENT",
                        card.replaceAll("\\s+", "") + ":" + round,
                        purchaseDate.toString(), purchaseAmount, merchant);
                int occurrence = occurrences.merge(baseKey, 1, Integer::sum);
                String key = CardStatementParserSupport.transactionKey("SAMSUNG_STATEMENT",
                        baseKey + ":" + occurrence, purchaseDate.toString(), purchaseAmount, merchant);
                result.add(new CardSpendingImportRow(i + 1, spendingDate, merchant, amount, key, error,
                        sheet.getSheetName()));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException(sheet.getSheetName() + " " + (i + 1) + "행: 이용일 형식이 올바르지 않습니다.");
            } catch (ArithmeticException e) {
                throw new IllegalArgumentException(sheet.getSheetName() + " " + (i + 1) + "행: 금액 또는 할부 회차를 확인해 주세요.");
            }
        }
    }

    private int positiveInteger(Row row, int column) {
        return Math.toIntExact(CardStatementParserSupport.positiveAmount(row, column));
    }

    private long nonNegativeAmount(Row row, int column, boolean allowBlank) {
        String value = CardStatementParserSupport.text(row, column).replace(",", "");
        if (value.isBlank() && allowBlank) return 0;
        try {
            long amount = new BigDecimal(value).longValueExact();
            if (amount < 0) throw new ArithmeticException();
            return amount;
        } catch (NumberFormatException | ArithmeticException e) {
            throw new IllegalArgumentException((row.getRowNum() + 1) + "행: 원금과 이자/수수료는 0 이상의 정수여야 합니다.");
        }
    }

    @Override
    public List<CardSpendingImportRow> parse(Sheet sheet) {
        Row usageHeader = findUsageHeader(sheet);
        if (usageHeader != null) return parseUsage(sheet, usageHeader);
        Map<String, Integer> h = CardStatementParserSupport.headers(sheet.getRow(0));
        CardStatementParserSupport.requireHeaders(h, "삼성카드", "매출상품명", "할부개월수", "매출일자",
                "매출금액(원)", "가맹점명", "가맹점번호", "승인번호");
        List<CardSpendingImportRow> rows = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            String dateText = CardStatementParserSupport.text(row, h.get("매출일자"));
            if (dateText.isBlank()) continue;
            try {
                LocalDate date = LocalDate.parse(dateText, DATE);
                String merchant = CardStatementParserSupport.text(row, h.get("가맹점명"));
                String approval = CardStatementParserSupport.text(row, h.get("승인번호"));
                long amount = CardStatementParserSupport.positiveAmount(row, h.get("매출금액(원)"));
                String product = CardStatementParserSupport.text(row, h.get("매출상품명"));
                String installment = CardStatementParserSupport.text(row, h.get("할부개월수"));
                String error = null;
                if (!"일시불".equals(product) || !(installment.isBlank() || "0".equals(installment)))
                    error = "할부 또는 특수 거래는 첫 버전에서 지원하지 않습니다.";
                else if (merchant.isBlank() || approval.isBlank()) error = "가맹점명 또는 승인번호가 없습니다.";
                else if (merchant.length() > 200) error = "가맹점명이 200자를 초과합니다.";
                else if (date.isAfter(LocalDate.now())) error = "미래 거래는 등록할 수 없습니다.";
                String key = CardStatementParserSupport.transactionKey("SAMSUNG", approval,
                        date.toString(), amount, merchant);
                rows.add(new CardSpendingImportRow(i + 1, date, merchant, amount, key, error));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException((i + 1) + "행: 삼성카드 매출일자 형식이 올바르지 않습니다.");
            }
        }
        return rows;
    }
}
