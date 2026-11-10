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
                      com.deveopsj.income.entity.Income.IncomeType incomeType, String description, LocalDate bookingDate, String error, ForeignAmounts foreignAmounts) {
        public Row(int line,String name,String typeLabel,LocalDate date,TradeType type,BigDecimal quantity,BigDecimal price,long gross,long settlement,long fee,long tax,com.deveopsj.income.entity.Income.IncomeType incomeType,String description,LocalDate bookingDate,String error) {
            this(line,name,typeLabel,date,type,quantity,price,gross,settlement,fee,tax,incomeType,description,bookingDate,error,null);
        }
        public boolean foreignFlow() { return foreignAmounts!=null && foreignAmounts.flowType()!=null; }
        public boolean overseas() { return foreignAmounts != null; }
        public String sourceIdentity() { return overseas() && foreignAmounts.detailed() ? "ISIN:"+foreignAmounts.sourceCode() : name.strip().replaceAll("[\\s\\u3000]+"," "); }
        public boolean eligible() { return error.isEmpty(); }
        public boolean income() { return incomeType != null; }
        public boolean transfer() { return eligible() && type == null && !income() && Set.of("이체입금", "이체출금").contains(description); }
    }
    public record ForeignAmounts(BigDecimal gross, BigDecimal settlement, BigDecimal fee, BigDecimal tax,String sourceCode,BigDecimal sourceRate,String flowType,long sourceKrwTax) {
        public ForeignAmounts(BigDecimal gross,BigDecimal settlement,BigDecimal fee,BigDecimal tax,String sourceCode,BigDecimal sourceRate) { this(gross,settlement,fee,tax,sourceCode,sourceRate,null,0); }
        public ForeignAmounts(BigDecimal gross,BigDecimal settlement,BigDecimal fee,BigDecimal tax) { this(gross,settlement,fee,tax,null,null); }
        public boolean detailed() { return sourceCode!=null; }
    }
    private static final List<String> FOREIGN_HEADERS=List.of("실거래일자","거래유형","종목명","수량","거래금액(외화)","외화잔고(전)","정산금액(외화)","외화잔고(후)","유가증권잔고(전)","담보금(외화)","국내수수료(외화)","해외세액(외화)","거래세/소득세(원화)","통화","거래일자");
    private static final List<String> FOREIGN_SUBHEADERS=List.of("","상세내용","종목코드","단가","거래금액(원화)","원화잔고(전)","정산금액(원화)","원화잔고(후)","유가증권잔고(후)","담보금(원화)","국내수수료(원화)","인지세(외화)","주민세/농특세(원화)","환율","");
    public List<Row> parse(byte[] bytes) {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 거래내역 파일을 선택하세요.");
        try {
            List<List<String>> table;
            if (bytes[0] == 'P' || (bytes[0] & 255) == 0xD0) table = workbook(bytes);
            else {
                String source=decode(bytes);
                if(!source.stripLeading().startsWith("<") && source.contains("\t")) {
                    table=source.lines().filter(line -> !line.isBlank()).map(line -> Arrays.stream(line.split("\t",-1)).map(String::strip).toList()).toList();
                } else table=html(bytes);
            }
            int foreignHeader=table.indexOf(FOREIGN_HEADERS);
            if(foreignHeader>=0) return detailed(table,foreignHeader);
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
            if(result.size()>2000) throw new IllegalArgumentException("한 번에 2,000건까지 가져올 수 있습니다.");
            if (result.isEmpty()) throw new IllegalArgumentException("거래내역이 없습니다.");
            return List.copyOf(result);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("파일을 읽을 수 없습니다. 암호 없는 나무 종합거래내역 파일을 선택하세요."); }
    }
    private List<Row> detailed(List<List<String>> table,int header) {
        table=table.stream().map(cells -> {
            if(cells.size()==14 && cells.get(0).isBlank()) { var padded=new ArrayList<>(cells);padded.add("");return (List<String>)padded; }
            return cells;
        }).toList();
        if(header+1>=table.size() || !table.get(header+1).equals(FOREIGN_SUBHEADERS)) throw new IllegalArgumentException("해외주식거래내역의 두 줄 머리글을 확인하세요.");
        var result=new ArrayList<Row>();
        for(int i=header+2;i<table.size();i+=2) {
            if(i+1>=table.size() || table.get(i).size()!=15 || table.get(i+1).size()!=15) throw new IllegalArgumentException("해외 거래는 한 건이 두 행입니다. 원본 전체를 업로드하세요.");
            var a=table.get(i); var b=table.get(i+1);String name=a.get(2),label=a.get(1);
            if(!b.get(0).isBlank() || !b.get(14).isBlank()) throw new IllegalArgumentException("해외 거래의 두 행이 올바르게 연결되지 않았습니다.");
            if(!"USD".equals(a.get(13))) { result.add(rejected(i+1,name,label,"원화 내역은 종합거래내역에서 등록하세요. 해외 양식은 USD만 지원합니다."));continue; }
            if(!Set.of("매수","매도").contains(label)) {
                String detail=b.get(1),flow=null;
                if(label.equals("입금")) flow=switch(detail) { case "소수점해외배당금","소수점해외배당","외화배당금입금" -> "DIVIDEND";case "외화예탁금이용료" -> "INTEREST";case "외화이체입금","이체입금" -> "DEPOSIT";default -> null; };
                if(label.equals("출금") && Set.of("외화이체출금","이체출금").contains(detail)) flow="WITHDRAWAL";
                if(flow==null) { result.add(rejected(i+1,name,label,"환전 또는 지원하지 않는 해외 내역입니다. 계좌 입출금에 합산하지 않습니다."));continue; }
                try {
                    var gross=number(a.get(4),15,4,false);var net=number(a.get(6),15,4,true);
                    var fee=number(a.get(10),15,4,false);var tax=number(a.get(11),15,4,false).add(number(b.get(11),15,4,false));
                    long krwTax=number(a.get(12),15,0,false).add(number(b.get(12),15,0,false)).longValueExact();
                    var rate=b.get(13).isBlank()?null:number(b.get(13),13,6,false);
                    if(name.length()>100 || b.get(2).length()>20) throw new IllegalArgumentException();
                    result.add(new Row(i+1,name,label,date(a.get(0)),null,BigDecimal.ZERO,BigDecimal.ZERO,0,0,0,0,null,detail,date(a.get(14)),"",new ForeignAmounts(gross,net,fee,tax,b.get(2),rate,flow,krwTax)));
                } catch(RuntimeException e) { result.add(rejected(i+1,name,label,"해외 입출금·배당의 날짜와 외화 정산금액을 확인하세요.")); }
                continue;
            }
            try {
                String detail=b.get(1),code=b.get(2);
                if(!Set.of("외화증권매수","외화증권매도","소수점해외주식매수","소수점해외주식매도").contains(detail) || !detail.endsWith(label)
                    || !code.matches("US[A-Z0-9]{9}[0-9]") || name.isBlank() || name.length()>100) throw new IllegalArgumentException();
                var quantity=number(a.get(3),11,8,true);var price=number(b.get(3),15,4,true);
                var gross=number(a.get(4),15,4,true);var net=number(a.get(6),15,4,false);
                var fee=number(a.get(10),15,4,false);var tax=number(a.get(11),15,4,false).add(number(b.get(11),15,4,false));
                if(number(a.get(12),15,0,false).signum()!=0 || number(b.get(10),15,0,false).signum()!=0 || number(b.get(12),15,0,false).signum()!=0) {
                    result.add(rejected(i+1,name,label,"별도 원화 수수료·세금이 있는 해외 매매입니다. 비용 확인이 필요합니다."));continue;
                }
                if(quantity.multiply(price).subtract(gross).abs().compareTo(new BigDecimal("0.01"))>0 || net.compareTo(label.equals("매수")?gross.add(fee).add(tax):gross.subtract(fee).subtract(tax))!=0) throw new IllegalArgumentException();
                var rate=b.get(13).isBlank()?null:number(b.get(13),13,6,false);
                var amounts=new ForeignAmounts(gross,net,fee,tax,code,rate);
                result.add(new Row(i+1,name,label,date(a.get(0)),label.equals("매수")?TradeType.BUY:TradeType.SELL,quantity,price,0,0,0,0,null,detail,date(a.get(14)),"",amounts));
            } catch(RuntimeException e) { result.add(rejected(i+1,name,label,"해외 거래의 날짜·ISIN·수량·단가·외화 정산액을 확인하세요.")); }
        }
        if(result.isEmpty() || result.size()>2000) throw new IllegalArgumentException("해외 거래는 한 번에 1~2,000건까지 가져올 수 있습니다.");
        return List.copyOf(result);
    }
    private String decode(byte[] bytes) throws CharacterCodingException {
        try { return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().replace("\uFEFF",""); }
        catch(CharacterCodingException e) { return Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
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
            if (("입금".equals(label) && "이체입금".equals(c.get(2)))
                    || ("출금".equals(label) && "이체출금".equals(c.get(2)))) {
                var date = date(c.get(0)); var booking = date(c.get(12));
                long gross = number(c.get(6),15,0,true).longValueExact();
                long settlement = number(c.get(7),15,0,true).longValueExact();
                long fee = number(c.get(10),15,0,false).longValueExact();
                long tax = number(c.get(11),15,0,false).longValueExact();
                long expected = "입금".equals(label) ? Math.subtractExact(gross,Math.addExact(fee,tax)) : Math.addExact(gross,Math.addExact(fee,tax));
                if (settlement != expected || name.length()>100) throw new IllegalArgumentException();
                return new Row(line,name,label,date,null,BigDecimal.ZERO,BigDecimal.ZERO,gross,settlement,fee,tax,null,c.get(2),booking,"");
            }
            if (!Set.of("매수", "매도").contains(label)) return rejected(line,name,label,"지원하지 않는 내역: " + c.get(2));
            String detail = c.get(2);
            if ("퇴직연금계약".equals(detail)) return rejected(line,name,label,"IRP 계약 처리 내역입니다. 수량·단가가 없어 주식 매매로 저장하지 않습니다. 같은 금액의 이체입금은 계좌 입금으로 한 번만 등록하세요.");
            if (Set.of("외화증권매수","외화증권매도","소수점해외주식매수","소수점해외주식매도").contains(detail) && detail.endsWith(label)) {
                var quantity=number(c.get(4),11,8,true); var price=number(c.get(5),15,4,false);
                var amounts=new ForeignAmounts(number(c.get(6),15,4,false),number(c.get(7),15,4,false),number(c.get(10),15,4,false),number(c.get(11),15,4,false));
                if(name.isBlank() || name.length()>100) throw new IllegalArgumentException();
                return new Row(line,name,label,date(c.get(0)),label.equals("매수")?TradeType.BUY:TradeType.SELL,quantity,price,0,0,0,0,null,detail,date(c.get(12)),"",amounts);
            }
            if (!Set.of("코스피매수", "코스피매도", "코스닥매수", "코스닥매도").contains(detail)
                    || !detail.endsWith(label)) return rejected(line, name, label, "지원하지 않는 거래 유형: " + detail + "");
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
    List<List<String>> workbook(byte[] bytes) throws IOException {
        try (Workbook book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() != 1) throw new IllegalArgumentException("시트가 하나인 원본 거래내역 파일을 선택하세요.");
            var sheet = book.getSheetAt(0); var rows = new ArrayList<List<String>>(); var format = new DataFormatter(Locale.KOREA);
            if (sheet.getLastRowNum() > 4003) throw new IllegalArgumentException("한 번에 2,000행까지 가져올 수 있습니다.");
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
    List<List<String>> html(byte[] bytes) throws IOException {
        String source;
        try { source = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        catch (CharacterCodingException e) { source = Charset.forName("MS949").newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
        if (!source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<!doctype html") && !source.stripLeading().toLowerCase(Locale.ROOT).startsWith("<html"))
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다.");
        var rows = new ArrayList<List<String>>();
        new ParserDelegator().parse(new StringReader(source), new HTMLEditorKit.ParserCallback() {
            List<String> row; StringBuilder cell;
            final int[] pendingRows = new int[30];
            final BitSet occupied = new BitSet(30);
            int column, rowSpan, colSpan;
            private int span(MutableAttributeSet attributes, HTML.Attribute name) {
                Object raw=attributes.getAttribute(name);
                if(raw==null) return 1;
                try {
                    int value=Integer.parseInt(raw.toString());
                    if(value<1 || value>(name==HTML.Attribute.COLSPAN?30:4004)) throw new NumberFormatException();
                    return value;
                } catch(NumberFormatException e) { throw new IllegalArgumentException("병합 셀 범위를 확인하세요."); }
            }
            private void ensureColumn(int index) { while(row.size()<=index) row.add(""); }
            @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attributes, int pos) {
                if (tag == HTML.Tag.TR) {
                    row=new ArrayList<>();occupied.clear();column=0;
                    for(int i=0;i<pendingRows.length;i++) if(pendingRows[i]>0) {
                        occupied.set(i);ensureColumn(i);pendingRows[i]--;
                    }
                }
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row!=null) {
                    column=occupied.nextClearBit(column);
                    rowSpan=span(attributes,HTML.Attribute.ROWSPAN);colSpan=span(attributes,HTML.Attribute.COLSPAN);
                    if(column+colSpan>30) throw new IllegalArgumentException("거래내역 열 수를 확인하세요.");
                    for(int i=column;i<column+colSpan;i++) if(occupied.get(i)) throw new IllegalArgumentException("병합 셀이 겹칩니다.");
                    cell=new StringBuilder();
                }
            }
            @Override public void handleText(char[] data, int pos) { if (cell != null) cell.append(data); }
            @Override public void handleEndTag(HTML.Tag tag, int pos) {
                if ((tag == HTML.Tag.TD || tag == HTML.Tag.TH) && row != null && cell != null) {
                    for(int i=column;i<column+colSpan;i++) {
                        ensureColumn(i);occupied.set(i);pendingRows[i]=rowSpan-1;
                    }
                    row.set(column,cell.toString().replace('\u00a0',' ').strip());
                    column+=colSpan;cell=null;
                }
                if (tag == HTML.Tag.TR && row != null) { rows.add(List.copyOf(row)); row = null; }
            }
        }, true);
        if (rows.size() > 4004) throw new IllegalArgumentException("한 번에 2,000행까지 가져올 수 있습니다.");
        return rows;
    }
}
