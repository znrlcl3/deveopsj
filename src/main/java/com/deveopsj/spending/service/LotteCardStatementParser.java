package com.deveopsj.spending.service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.deveopsj.common.util.HtmlExcelTableReader;
import com.deveopsj.spending.dto.CardInstallmentPlan;
import com.deveopsj.spending.dto.CardSpendingImportRow;

final class LotteCardStatementParser {
    private static final List<String> REQUIRED = List.of("이용일자", "이용시간", "이용카드", "이용가맹점",
            "이용금액", "이용구분", "할부개월", "승인번호", "취소여부", "취소일자", "취소금액");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private LotteCardStatementParser() {}

    static List<CardSpendingImportRow> parse(byte[] bytes) throws IOException {
        var table = HtmlExcelTableReader.read(bytes);
        var header = table.stream().filter(row -> row.containsAll(REQUIRED)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("롯데카드 카드이용내역의 상세 표를 확인해 주세요."));
        if (new HashSet<>(header).size() != header.size())
            throw new IllegalArgumentException("롯데카드 상세 표에 중복된 열이 있습니다.");
        List<Transaction> transactions = new ArrayList<>();
        Set<String> cancelled = new HashSet<>();
        for (int i = table.indexOf(header) + 1; i < table.size(); i++) {
            var cells = table.get(i);
            if (cells.stream().allMatch(String::isBlank)) continue;
            if (cells.size() != header.size())
                throw new IllegalArgumentException((i + 1) + "행: 롯데카드 상세 열을 확인해 주세요.");
            Transaction tx = new Transaction(i + 1, header, cells);
            transactions.add(tx);
            if (tx.hasCancellation() && !tx.get("승인번호").isBlank()) cancelled.add(tx.approvalKey());
        }
        return transactions.stream().map(tx -> tx.preview(cancelled)).toList();
    }

    private record Transaction(int line, List<String> header, List<String> cells) {
        String get(String name) { return cells.get(header.indexOf(name)); }

        long amount(String name) {
            String value = get(name);
            if (!value.matches("-?(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)"))
                throw invalid(name);
            try {
                return Long.parseLong(value.replace(",", ""));
            } catch (NumberFormatException e) {
                throw invalid(name);
            }
        }

        IllegalArgumentException invalid(String name) {
            return new IllegalArgumentException(line + "행: " + name + " 형식을 확인해 주세요.");
        }

        String approvalKey() {
            return CardStatementParserSupport.transactionKey("LOTTE", get("승인번호"), get("이용카드"), 0, get("이용가맹점"));
        }

        boolean hasCancellation() {
            return !get("취소여부").equals("N") || !get("취소일자").isBlank()
                    || amount("취소금액") != 0 || amount("이용금액") < 0;
        }

        CardSpendingImportRow preview(Set<String> cancelled) {
            LocalDate date;
            try {
                date = LocalDate.parse(get("이용일자"), DATE);
            } catch (DateTimeParseException e) {
                throw invalid("이용일자");
            }
            long amount = amount("이용금액");
            String merchant = get("이용가맹점");
            String key = CardStatementParserSupport.transactionKey("LOTTE", approvalKey(),
                    date + " " + get("이용시간"), amount, merchant);
            String error = null;
            CardInstallmentPlan installment = null;
            if (hasCancellation() || cancelled.contains(approvalKey())) {
                error = "취소 또는 취소 의심 거래입니다. 기존 지출과 잔여 금액을 확인하세요.";
            } else if (date.isAfter(LocalDate.now()) || merchant.isBlank() || merchant.length() > 200
                    || get("승인번호").isBlank() || amount == 0) {
                error = "이용일자·가맹점·승인번호·이용금액을 확인하세요.";
            } else if ("할부".equals(get("이용구분")) && get("할부개월").matches("[0-9]{1,2}")) {
                try {
                    installment = new CardInstallmentPlan(amount, Integer.parseInt(get("할부개월")), null);
                } catch (IllegalArgumentException e) {
                    error = e.getMessage();
                }
            } else if (!"일시불".equals(get("이용구분")) || !Set.of("", "-", "0", "00").contains(get("할부개월"))) {
                error = "지원하지 않는 이용구분 또는 할부개월입니다.";
            }
            return new CardSpendingImportRow(line, date, merchant, amount, key, error, null,
                    List.of(), false, false, installment);
        }
    }
}
