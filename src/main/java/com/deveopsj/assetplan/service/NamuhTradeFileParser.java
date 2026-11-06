package com.deveopsj.assetplan.service;

import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.*;
import javax.swing.text.html.parser.ParserDelegator;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;

@Component
public class NamuhTradeFileParser {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final List<String> HEADERS = List.of("실거래일자", "거래유형", "상세내용", "종목명", "수량", "단가", "거래금액", "정산금액", "잔고", "예수금잔액", "수수료", "세금", "거래일자");
    public record Row(int line, String name, String typeLabel, LocalDate date, TradeType type,
                      BigDecimal quantity, BigDecimal price, long gross, long settlement, long fee, long tax,
                      com.deveopsj.income.entity.Income.IncomeType incomeType, String description, LocalDate bookingDate, String error) {
        public boolean eligible() { return error.isEmpty(); }
        public boolean income() { return incomeType != null; }
    }
    public List<Row> parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 거래내역 파일을 선택하세요.");
        try {
            List<List<String>> table;
            if (bytes[0] == 'P' || (bytes[0] & 255) == 0xD0) table = workbook(bytes);
            else table = html(bytes);
            int header = table.indexOf(HEADERS);
            if (header < 0) throw new IllegalArgumentException("나무 종합거래내역(간략) 양식의 13개 열을 확인해 주세요.");
            var result = new ArrayList<Row>();
            for (int i = header + 1; i < table.size(); i++) {
                var cells = table.get(i);
                if (cells.stream().allMatch(String::isBlank)) continue;
                if (cells.equals(HEADERS)) continue;
                if (cells.size() != 13) throw new IllegalArgumentException((i + 1) + "행: 열 수가 다릅니다. 원본 파일을 다시 내려받으세요.");
                result.add(row(i + 1, cells));
            }
            if (result.isEmpty()) throw new IllegalArgumentException("거래내역이 없습니다.");
            return List.copyOf(result);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("파일을 읽을 수 없습니다. 암호 없는 나무 종합거래내역 파일을 선택하세요."); }
    }
    private Row row(int line, List<String> c) {
        String name = c.get(3), label = c.get(1);
        try {
            if ("입금".equals(label) && Set.of("ETF분배금입금", "예탁금이용료").contains(c.get(2))) {
                var date = date(c.get(0)); var booking = date(c.get(12));
                long gross = number(c.get(6), 15, 0, true).longValueExact();
                long settlement = number(c.get(7), 15, 0, true).longValueExact();
                long fee = number(c.get(10), 15, 0, false).longValueExact();
                long tax = number(c.get(11), 15, 0, false).longValueExact();
                boolean dividend = "ETF분배금입금".equals(c.get(2));
                if (name.length() > 100 || (dividend && name.isBlank()) || Math.subtractExact(gross, Math.addExact(fee, tax)) != settlement)
                    throw new IllegalArgumentException();
                return new Row(line, name, dividend ? "ETF 분배금" : "예탁금 이용료", date, null, BigDecimal.ZERO, BigDecimal.ZERO,
                        gross, settlement, fee, tax, dividend ? com.deveopsj.income.entity.Income.IncomeType.DIVIDEND : com.deveopsj.income.entity.Income.IncomeType.INTEREST,
                        c.get(2), booking, "");
            }
            if (!Set.of("매수", "매도").contains(label)) {
                return switch (c.get(2)) {
                    case "이체입금" -> rejected(line, name, "계좌 이체입금", "계좌로 옮겨온 돈입니다. 매매·배당 수입으로 등록하지 않습니다.");
                    case "이체출금" -> rejected(line, name, "계좌 이체출금", "계좌에서 옮겨간 돈입니다. 매매로 등록하지 않습니다.");
                    default -> rejected(line, name, label, "매매가 아닌 내역 제외: " + c.get(2));
                };
            }
            String detail = c.get(2);
            if (!Set.of("코스피매수", "코스피매도", "코스닥매수", "코스닥매도").contains(detail)
                    || !detail.endsWith(label)) return rejected(line, name, label, "지원하지 않는 거래: 국내 주식·ETF 매수/매도만 지원");
            LocalDate date = LocalDate.parse(c.get(0), DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(java.time.format.ResolverStyle.STRICT));
            if (date.isAfter(LocalDate.now()) || date.getYear() < 1900) throw new IllegalArgumentException();
            var quantity = number(c.get(4), 11, 8, true);
            var price = number(c.get(5), 15, 4, true);
            long gross = number(c.get(6), 15, 0, true).longValueExact();
            long settlement = number(c.get(7), 15, 0, false).longValueExact();
            long fee = number(c.get(10), 15, 0, false).longValueExact();
            long tax = number(c.get(11), 15, 0, false).longValueExact();
            if (name.isBlank() || name.length() > 100 || quantity.multiply(price).compareTo(BigDecimal.valueOf(gross)) != 0)
                throw new IllegalArgumentException();
            long expected = label.equals("매수") ? Math.addExact(gross, Math.addExact(fee, tax)) : Math.subtractExact(gross, Math.addExact(fee, tax));
            if (settlement != expected) throw new IllegalArgumentException();
            return new Row(line, name, label, date, label.equals("매수") ? TradeType.BUY : TradeType.SELL, quantity, price, gross, settlement, fee, tax, null, detail, date(c.get(12)), "");
        } catch (RuntimeException e) { return rejected(line, name, label, "날짜·수량·단가·수수료·정산금액을 확인하세요. 금액이 일치하지 않거나 형식이 올바르지 않습니다."); }
    }
    private Row rejected(int line, String name, String label, String reason) {
        return new Row(line, name, label, null, null, null, null, 0, 0, 0, 0, null, "", null, reason);
    }
    private LocalDate date(String text) {
        var date = LocalDate.parse(text, DateTimeFormatter.ofPattern("uuuu.MM.dd").withResolverStyle(java.time.format.ResolverStyle.STRICT));
        if (date.isAfter(LocalDate.now()) || date.getYear() < 1900) throw new IllegalArgumentException();
        return date;
    }
    private BigDecimal number(String text, int integers, int fraction, boolean positive) {
        if (!text.matches("(?:[0-9]+|[0-9]{1,3}(?:,[0-9]{3})+)(?:\\.[0-9]+)?")) throw new IllegalArgumentException();
        BigDecimal value = new BigDecimal(text.replace(",", "")).stripTrailingZeros();
        if (value.precision() - value.scale() > integers || value.scale() > fraction || (positive && value.signum() <= 0)) throw new IllegalArgumentException();
        return value;
    }
    private List<List<String>> workbook(byte[] bytes) throws IOException {
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() != 1) throw new IllegalArgumentException("시트가 하나인 원본 거래내역 파일을 선택하세요.");
            var sheet = book.getSheetAt(0); var rows = new ArrayList<List<String>>(); var format = new DataFormatter(Locale.KOREA);
            if (sheet.getLastRowNum() > 2000) throw new IllegalArgumentException("한 번에 2,000행까지 가져올 수 있습니다.");
            for (var row : sheet) {
                if (row.getLastCellNum() > 30) throw new IllegalArgumentException("거래내역 열 수를 확인하세요.");
                var values = new ArrayList<String>();
                for (int i = 0; i < row.getLastCellNum(); i++) {
                    var cell = row.getCell(i);
                    if (cell != null && cell.getCellType() == CellType.FORMULA) throw new IllegalArgumentException("수식이 포함된 파일은 지원하지 않습니다.");
                    values.add(format.formatCellValue(cell).strip());
                }
                rows.add(values);
            }
            return rows;
        }
    }
    private List<List<String>> html(byte[] bytes) throws IOException {
        String source;
        try { source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { source = Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        if (!source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<!doctype html") && !source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<html"))
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
        var rows = new ArrayList<List<String>>();
        new ParserDelegator().parse(new StringReader(source), new HTMLEditorKit.ParserCallback() {
            List<String> row; StringBuilder cell;
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.TR) row = new ArrayList<>();
                if (tag == HTML.Tag.TD || tag == HTML.Tag.TH) cell = new StringBuilder();
            }
            @Override public void handleText(char[] data, int pos) { if (cell != null) cell.append(data); }
            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row != null && cell != null) { row.add(cell.toString().replace('\u00a0', ' ').strip()); cell = null; }
                if (tag == HTML.Tag.TR && row != null) { rows.add(List.copyOf(row)); row = null; }
            }
        }, true);
        if (rows.size() > 2001) throw new IllegalArgumentException("한 번에 2,000행까지 가져올 수 있습니다.");
        return rows;
    }
}
