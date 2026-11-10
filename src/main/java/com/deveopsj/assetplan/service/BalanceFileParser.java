package com.deveopsj.assetplan.service;

import java.math.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
public class BalanceFileParser {
    private final NamuhTradeFileParser tables;
    private static final List<String> HEADER=List.of("No","잔고유형","상품명","상품코드","구분","수량","현재가","매입금액","평가금액","평가손익","수익률");
    public record Line(int number,String type,String name,String code,String category,BigDecimal quantity,
                       BigDecimal price,long cost,long value,long profit,BigDecimal rate) {}
    public record Parsed(Map<String,Long> totals,List<Line> lines) {
        public long total() { return totals.get("자산금액"); }
        public BigInteger securityValue() { return lines.stream().filter(l->!l.type().contains("예수금")).map(l->BigInteger.valueOf(l.value())).reduce(BigInteger.ZERO,BigInteger::add); }
        public BigInteger difference() { return securityValue().subtract(BigInteger.valueOf(totals.get("평가금액"))); }
        public long securities() { return lines.stream().filter(l->!l.type().contains("예수금")).map(Line::code).distinct().count(); }
    }
    public Parsed parse(byte[] bytes) {
        if(bytes.length==0 || bytes.length>NamuhTradeFileParser.MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 종합잔고 파일을 선택하세요.");
        try {
            var rows=(bytes[0]=='P' || (bytes[0]&255)==0xD0) ? tables.workbook(bytes):tables.html(bytes);
            int header=rows.indexOf(HEADER);
            if(header<0) throw new IllegalArgumentException("나무 종합잔고의 11개 열을 확인하세요. 종합거래내역과는 다른 파일입니다.");
            var totals=new LinkedHashMap<String,Long>();
            for(var row:rows.subList(0,header)) for(int i=0;i+1<row.size();i+=2) {
                String key=row.get(i);
                if(Set.of("자산금액","매입금액","평가금액","평가손익","출금가능금액","예수금","예수금(D+1)","예수금(D+2)","외화예수금").contains(key)) {
                    if(totals.putIfAbsent(key,number(row.get(i+1)).longValueExact())!=null) throw new IllegalArgumentException("계좌 요약 항목이 중복되어 있습니다.");
                }
            }
            if(!totals.keySet().containsAll(Set.of("자산금액","매입금액","평가금액","평가손익"))) throw new IllegalArgumentException("상단 계좌 자산금액·매입금액·평가금액·평가손익이 포함된 원본 파일을 선택하세요.");
            var lines=new ArrayList<Line>();var seen=new HashSet<Integer>();
            for(var r:rows.subList(header+1,rows.size())) {
                if(r.stream().allMatch(String::isBlank)) continue;
                if(r.size()!=11) throw new IllegalArgumentException("잔고 열 수가 다릅니다. 원본 파일을 확인하세요.");
                int no=number(r.get(0)).intValueExact();
                if(no<=0 || !seen.add(no)) throw new IllegalArgumentException("잔고 순번이 올바르지 않거나 중복되었습니다.");
                for(int c=1;c<=4;c++) if(r.get(c).isBlank() || r.get(c).length()>200) throw new IllegalArgumentException(no+"행: 상품 정보가 올바르지 않습니다.");
                var qty=number(r.get(5));var price=number(r.get(6));
                if(qty.signum()<0 || qty.scale()>8 || price.signum()<0) throw new IllegalArgumentException(no+"행: 수량·현재가를 확인하세요.");
                long cost=number(r.get(7)).longValueExact(),value=number(r.get(8)).longValueExact();
                if(cost<0 || value<0) throw new IllegalArgumentException(no+"행: 매입금액·평가금액을 확인하세요.");
                lines.add(new Line(no,r.get(1),r.get(2),r.get(3),r.get(4),qty,price,cost,value,number(r.get(9)).longValueExact(),number(r.get(10))));
                if(lines.size()>2000) throw new IllegalArgumentException("한 번에 2,000행까지 등록할 수 있습니다.");
            }
            if(lines.isEmpty()) throw new IllegalArgumentException("잔고 항목이 없습니다.");
            return new Parsed(Collections.unmodifiableMap(totals),List.copyOf(lines));
        } catch(java.io.IOException | ArithmeticException e) { throw new IllegalArgumentException("잔고 파일의 숫자·형식을 확인하세요."); }
    }
    private BigDecimal number(String raw) {
        String s=raw.replace(",","").replace("원","").replace("%","").strip();
        if(!s.matches("-?[0-9]{1,18}(\\.[0-9]{1,8})?")) throw new IllegalArgumentException("숫자 형식을 확인하세요: "+raw.substring(0,Math.min(raw.length(),30)));
        return new BigDecimal(s);
    }
}
