package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.entity.*;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class NamuhTradeImportService {
    private final ForeignAccountEntryRepository foreignEntries;
    private final NamuhSymbolMappingRepository mappings;
    private final AccountTransferRepository transfers;
    private final NamuhTradeFileParser parser;
    private final AssetPlanRepository plans;
    private final AssetTradeRepository trades;
    private final InvestmentAssetRepository assets;
    private final com.deveopsj.income.repository.IncomeRepository incomes;
    public record Candidate(Long id, String label, boolean historyOnly, boolean duplicate, long existingCount) {}
    public record PreviewRow(NamuhTradeFileParser.Row source, List<Candidate> candidates, String reason, String linkedSymbol, Long linkedAssetId, List<NamuhAssetMatcher.Option> foreignOptions, String matchMessage) {
        public PreviewRow(NamuhTradeFileParser.Row source,List<Candidate> candidates,String reason,String linkedSymbol) { this(source,candidates,reason,linkedSymbol,null,List.of(),null); }
        public PreviewRow(NamuhTradeFileParser.Row source,List<Candidate> candidates,String reason) { this(source,candidates,reason,null); }
        public boolean eligible() { return reason.isEmpty() && candidates.stream().anyMatch(c -> !c.duplicate()); }
        public boolean confirmable() { return source.eligible() && !reason.isEmpty() && !candidates.isEmpty(); }
        public Long defaultAssetId() {
            return (!source.overseas() || source.foreignFlow() || linkedSymbol!=null) && eligible() && candidates.size() == 1 && !candidates.get(0).duplicate()
                    ? candidates.get(0).id() : null;
        }
    }
    public record Preview(Long planId, String planName, List<PreviewRow> rows) {}
    public record ForeignInput(String symbol,Long assetId) { public ForeignInput(String symbol) { this(symbol,null); } }
    public record Selection(int index, Long assetId, boolean confirmedDuplicate, ForeignInput foreign) {
        public Selection(int index, Long assetId, boolean confirmedDuplicate) { this(index,assetId,confirmedDuplicate,null); }
        public Selection(int index, Long assetId) { this(index,assetId,false); }
    }

    @Transactional(readOnly=true)
    public List<NamuhAssetMatcher.Option> searchForeign(String keyword) {
        if(keyword==null || keyword.strip().length()<2 || keyword.length()>100) return List.of();
        String key=NamuhAssetMatcher.normalize(keyword);
        return assets.findByActiveTrueAndCurrency("USD").stream().filter(this::supported)
            .filter(a->NamuhAssetMatcher.normalize(a.getAssetName()).contains(key) || a.getSymbol().toUpperCase(Locale.ROOT).contains(keyword.strip().toUpperCase(Locale.ROOT)))
            .sorted(Comparator.comparing(InvestmentAsset::getSymbol).thenComparing(InvestmentAsset::getId)).limit(20).map(NamuhAssetMatcher::option).toList();
    }
    @Transactional(readOnly = true)
    public Preview preview(byte[] bytes, Long planId, Long memberId) {
        var plan = plans.findByIdAndMemberMemberId(planId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("본인의 플랜을 선택해 주세요."));
        var rows = parser.parse(bytes);
        var existing = trades.findByAssetPlanIdOrderByTradeDateAscIdAsc(planId);
        var frequency = rows.stream().filter(NamuhTradeFileParser.Row::eligible)
                .collect(Collectors.groupingBy(r -> key(r, planId, memberId), Collectors.counting()));
        var remembered = mappingMap(memberId);
        var usAssets=rows.stream().anyMatch(r->r.overseas() && !r.foreignFlow()) ? assets.findByActiveTrueAndCurrency("USD"):List.<InvestmentAsset>of();
        var matches=new HashMap<String,NamuhAssetMatcher.Match>();
        var result = new ArrayList<PreviewRow>();
        for (var row : rows) {
            if (!row.eligible()) { result.add(new PreviewRow(row, List.of(), row.error())); continue; }
            if(row.overseas() && !row.foreignAmounts().detailed()) { result.add(new PreviewRow(row,List.of(),"종합거래내역의 해외 매매는 제외합니다. 같은 플랜에 해외주식거래내역 파일을 업로드하세요."));continue; }
            String key = key(row, planId, memberId);
            long fileCount = frequency.get(key);
            List<Candidate> candidates;
            if(row.foreignFlow()) {
                long count=foreignEntries.countByImportGroupKey(key);
                candidates=List.of(new Candidate(0L,"USD "+ForeignAccountEntry.Type.valueOf(row.foreignAmounts().flowType())+" · 계좌 기록",false,count>0,count));
            } else if (row.overseas()) {
                long count=foreignDuplicateCount(row,existing,key);
                candidates=List.of(new Candidate(0L,"미국 주식·ETF (USD) · 상세 확인 후 저장",false,count>0,count));
            } else if (row.transfer()) {
                long count=transfers.countByImportKeyOrImportGroupKey(key,key);
                candidates=List.of(new Candidate(0L,"계좌 " + row.typeLabel() + " 기록",false,count>0,count));
            } else if (row.income()) {
                long count=incomeDuplicateCount(row,planId,memberId);
                candidates=List.of(new Candidate(0L,"수입 등록 · " + row.incomeType().getLabel(),false,count>0,count));
            } else {
                candidates=assets.findByActiveTrueAndAssetNameAndCurrency(row.name(),"KRW").stream()
                    .filter(a -> Set.of("ETF","KOSPI","KOSDAQ").contains(a.getMarket()))
                    .map(a -> {
                        long count=tradeDuplicateCount(row,a.getId(),existing,key);
                        return new Candidate(a.getId(),"["+a.getSymbol()+"] "+a.getAssetName()+" · "+a.getMarket(),historyOnly(row,a.getId(),existing),count>0,count);
                    }).toList();
            }
            long stored=candidates.stream().mapToLong(Candidate::existingCount).max().orElse(0);
            String reason=candidates.isEmpty() ? "연결할 국내 종목이 없습니다. 종목 목록을 등록·동기화한 뒤 다시 업로드하세요."
                : fileCount>1 || stored>0 ? "중복 의심 · 파일 안 동일 조건 " + fileCount + "건 · 기존 저장 최대 " + stored + "건. 실제 별도 거래인 행만 확인하세요." : "";
            var link=remembered.get(row.sourceIdentity());
            InvestmentAsset resolved=null;List<NamuhAssetMatcher.Option> options=List.of();String matchMessage=null;
            if(row.overseas() && !row.foreignFlow()) {
                if(link!=null && supported(link.getAsset())) { resolved=link.getAsset();options=List.of(NamuhAssetMatcher.option(resolved));matchMessage="저장된 ISIN 연결을 적용했습니다."; }
                else {
                    var match=matches.computeIfAbsent(row.name(),name->NamuhAssetMatcher.match(name,usAssets));
                    resolved=match.automatic();options=match.options();
                    matchMessage=resolved!=null ? "종목명 일치로 자동 연결했습니다. 저장하면 ISIN 연결을 기억합니다." : "자동으로 확정하지 못했습니다. 종목명·시장·티커를 확인하고 후보를 선택하세요.";
                }
            }
            result.add(new PreviewRow(row,candidates,reason,resolved==null?null:resolved.getSymbol(),resolved==null?null:resolved.getId(),options,matchMessage));
        }
        return new Preview(planId, plan.getPlanName(), List.copyOf(result));
    }

    @Transactional
    public int apply(Preview preview, List<Selection> selections, Long memberId) {
        if (selections == null || selections.isEmpty() || selections.size() > 2000)
            throw new IllegalArgumentException("저장할 거래와 종목을 선택하세요.");
        var owner = incomes.lockOwner(memberId).orElseThrow(() -> new IllegalArgumentException("회원을 확인할 수 없습니다."));
        var plan = plans.lockOwned(preview.planId(), memberId)
                .orElseThrow(() -> new IllegalArgumentException("본인의 플랜을 선택해 주세요."));
        var existing = trades.findByAssetPlanIdOrderByTradeDateAscIdAsc(plan.getId());
        var additions = new ArrayList<AssetTrade>(); var indices = new HashSet<Integer>();
        var remembered=mappingMap(memberId);
        var selectedSymbols=new HashMap<String,ForeignInput>();
        for(var selection:selections) {
            if(selection.index()>=0 && selection.index()<preview.rows().size() && preview.rows().get(selection.index()).source().overseas() && selection.foreign()!=null && (selection.foreign().assetId()!=null || (selection.foreign().symbol()!=null && !selection.foreign().symbol().isBlank()))) {
                String name=preview.rows().get(selection.index()).source().sourceIdentity();
                ForeignInput choice=selection.foreign();
                ForeignInput previous=selectedSymbols.putIfAbsent(name,choice);
                if(previous!=null && !previous.equals(choice)) throw new IllegalArgumentException("같은 원본 종목명은 하나의 종목코드로 연결하세요.");
            }
        }
        var foreignAdditions=new ArrayList<ForeignAccountEntry>();
        var transferAdditions = new ArrayList<AccountTransfer>();
        var incomeAdditions = new ArrayList<com.deveopsj.income.entity.Income>();
        for (var selection : selections) {
            if (!indices.add(selection.index()) || selection.index() < 0 || selection.index() >= preview.rows().size())
                throw new IllegalArgumentException("선택한 행이 올바르지 않습니다.");
            var view = preview.rows().get(selection.index()); var row = view.source();
            if (!view.eligible() && !(view.confirmable() && selection.confirmedDuplicate()))
                throw new IllegalArgumentException("저장할 수 없는 행이거나 중복 확인이 필요합니다.");
            if (selection.confirmedDuplicate() && !view.confirmable()) throw new IllegalArgumentException("중복 확인 대상이 아닙니다.");
            var candidate = view.candidates().stream().filter(c -> c.id().equals(selection.assetId()) && (!c.duplicate() || selection.confirmedDuplicate())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("미리보기의 종목을 선택하세요."));
            String groupKey=key(row,plan.getId(),memberId);
            String saveKey=selection.confirmedDuplicate() ? confirmedKey(groupKey) : groupKey;
            if(row.foreignFlow()) {
                if(foreignEntries.countByImportGroupKey(groupKey)!=candidate.existingCount()) throw new IllegalArgumentException("해외 배당·입출금 건수가 변경되었습니다. 다시 업로드하세요.");
                var entry=new ForeignAccountEntry();entry.setAssetPlan(plan);entry.setType(ForeignAccountEntry.Type.valueOf(row.foreignAmounts().flowType()));entry.setEntryDate(row.date());
                entry.setAmount(row.foreignAmounts().settlement());entry.setImportKey(saveKey);entry.setImportGroupKey(groupKey);
                entry.setSourceDetails(row.name()+" / "+row.description()+" / "+row.foreignAmounts()+" / 원본 거래일자: "+row.bookingDate());foreignAdditions.add(entry);continue;
            }
            if (row.overseas()) {
                if (foreignDuplicateCount(row,existing,groupKey)!=candidate.existingCount()) throw new IllegalArgumentException("해외 거래 건수가 변경되었습니다. 다시 업로드하세요.");
                var input=selection.foreign();
                String name=row.sourceIdentity();
                var rememberedMapping=remembered.get(name);
                input=selectedSymbols.get(name);
                if(input==null && view.linkedAssetId()!=null) input=new ForeignInput(null,view.linkedAssetId());
                if(input==null && rememberedMapping!=null && supported(rememberedMapping.getAsset())) input=new ForeignInput(null,rememberedMapping.getAsset().getId());
                var foreignTrade=foreignTrade(row,input,plan,existing,saveKey,groupKey);
                additions.add(foreignTrade);
                if(rememberedMapping==null) { rememberedMapping=new NamuhSymbolMapping(); rememberedMapping.setMember(owner);rememberedMapping.setSourceName(name);remembered.put(name,rememberedMapping); }
                rememberedMapping.setAsset(foreignTrade.getInvestmentAsset());
                continue;
            }
            if (row.transfer()) {
                if (transfers.countByImportKeyOrImportGroupKey(groupKey,groupKey) != candidate.existingCount())
                    throw new IllegalArgumentException("이미 등록된 입출금이 있거나 건수가 변경되었습니다. 다시 업로드하세요.");
                var transfer=new AccountTransfer(); transfer.setAssetPlan(plan);
                transfer.setType("입금".equals(row.typeLabel()) ? AccountTransfer.Type.DEPOSIT : AccountTransfer.Type.WITHDRAWAL);
                transfer.setTransferDate(row.date()); transfer.setBookingDate(row.bookingDate()); transfer.setAmount(row.settlement());
                transfer.setGrossAmount(row.gross()); transfer.setFee(row.fee()); transfer.setTax(row.tax());
                transfer.setDescription(row.description()); transfer.setSourceName(row.name()); transfer.setImportKey(saveKey); transfer.setImportGroupKey(groupKey);
                transferAdditions.add(transfer); continue;
            }
            if (row.income()) {
                if (incomeDuplicateCount(row, plan.getId(), memberId) != candidate.existingCount())
                    throw new IllegalArgumentException("이미 등록되었거나 중복 의심 수입이 있습니다. 다시 업로드하세요.");
                var income = new com.deveopsj.income.entity.Income();
                income.setMember(owner); income.setIncomeDate(row.date()); income.setIncomeType(row.incomeType()); income.setAmount(row.settlement());
                income.setMemo("나무 · " + row.typeLabel() + (row.name().isBlank() ? "" : " · " + row.name()));
                income.setImportKey(saveKey); income.setImportGroupKey(groupKey); income.setSourcePlanId(plan.getId()); income.setSourcePlanName(plan.getPlanName());
                income.setSourceSecurityName(row.name()); income.setSourceDescription(row.description()); income.setSourceBookingDate(row.bookingDate());
                income.setSourceGrossAmount(row.gross()); income.setSourceFee(row.fee()); income.setSourceTax(row.tax());
                incomeAdditions.add(income);
                continue;
            }
            var asset = assets.findByIdAndActiveTrue(candidate.id()).filter(a -> "KRW".equals(a.getCurrency())
                    && a.getAssetName().equalsIgnoreCase(row.name()) && Set.of("ETF", "KOSPI", "KOSDAQ").contains(a.getMarket()))
                    .orElseThrow(() -> new IllegalArgumentException("종목 정보가 변경되었습니다. 다시 업로드하세요."));
            if (tradeDuplicateCount(row,asset.getId(),existing,groupKey) != candidate.existingCount())
                throw new IllegalArgumentException("이미 등록된 거래가 있습니다. 다시 업로드해 중복을 확인하세요.");
            boolean history = historyOnly(row, asset.getId(), existing);
            if (history != candidate.historyOnly()) throw new IllegalArgumentException("보유분 기준이 변경되었습니다. 다시 업로드하세요.");
            if (row.date().isAfter(LocalDate.now())) throw new IllegalArgumentException("거래일을 확인하세요.");
            var trade = new AssetTrade(); trade.setAssetPlan(plan); trade.setInvestmentAsset(asset);
            trade.setTradeDate(row.date()); trade.setTradeType(row.type()); trade.setQuantity(row.quantity());
            trade.setUnitPrice(row.price()); trade.setCurrency("KRW"); trade.setExchangeRate(BigDecimal.ONE);
            trade.setFeeKrw(row.fee()); trade.setTaxKrw(row.tax()); trade.setSettlementAmountKrw(row.settlement());
            trade.setImportKey(saveKey); trade.setImportGroupKey(groupKey); trade.setHistoryOnly(history); trade.setMemo("나무 · " + row.description() + (history ? " · 내역만 저장" : ""));
            additions.add(trade);
        }
        // Same-day source execution order is unavailable: put buys first, and disclose day-level precision.
        additions.sort(Comparator.comparing(AssetTrade::getTradeDate).thenComparing(t -> t.getTradeType() == TradeType.BUY ? 0 : 1));
        validateQuantities(existing, additions);
        mappings.saveAll(remembered.values());
        trades.saveAllAndFlush(additions);
        incomes.saveAllAndFlush(incomeAdditions);
        transfers.saveAllAndFlush(transferAdditions);
        foreignEntries.saveAllAndFlush(foreignAdditions);
        return additions.size() + incomeAdditions.size() + transferAdditions.size()+foreignAdditions.size();
    }
    private Map<String,NamuhSymbolMapping> mappingMap(Long memberId) {
        var result=new HashMap<String,NamuhSymbolMapping>();
        for(var m:mappings.findByMemberMemberId(memberId)) result.put(m.getSourceName(),m);
        return result;
    }
    private boolean supported(InvestmentAsset asset) { return NamuhAssetMatcher.supported(asset); }
    private long foreignDuplicateCount(NamuhTradeFileParser.Row row,List<AssetTrade> existing,String key) {
        // Missing ticker/precision in the file: conservatively flag same-day, same-quantity USD trades too.
        return existing.stream().filter(t -> key.equals(t.getImportKey()) || key.equals(t.getImportGroupKey())
            || ("USD".equals(t.getCurrency()) && row.date().equals(t.getTradeDate()) && row.type()==t.getTradeType()
                && row.quantity().compareTo(t.getQuantity())==0)).count();
    }
    private AssetTrade foreignTrade(NamuhTradeFileParser.Row row,ForeignInput input,AssetPlan plan,List<AssetTrade> existing,String saveKey,String groupKey) {
        if(input==null || !row.foreignAmounts().detailed()) throw new IllegalArgumentException(row.line()+"행: 해외 종목·실제 달러 금액을 확인해 주세요.");
        String symbol=input.symbol()==null?"":input.symbol().strip().toUpperCase(Locale.ROOT);
        InvestmentAsset asset;
        if(input.assetId()!=null) asset=assets.findByIdAndActiveTrue(input.assetId()).filter(this::supported)
            .orElseThrow(()->new IllegalArgumentException("선택한 미국 종목이 변경되었거나 사용할 수 없습니다."));
        else {
            if(!symbol.matches("[A-Z0-9.\\-]{1,20}")) throw new IllegalArgumentException("미국 종목을 선택하세요.");
            var found=assets.findByActiveTrueAndSymbolAndCurrency(symbol,"USD").stream().filter(this::supported).toList();
            if(found.size()!=1) throw new IllegalArgumentException("미국 종목이 하나로 연결되지 않습니다. 후보를 선택하세요.");
            asset=found.get(0);
        }
        symbol=asset.getSymbol();
        var gross=foreignNumber(row.foreignAmounts().gross(),15,4,true);
        var fee=foreignNumber(row.foreignAmounts().fee(),15,4,false); var tax=foreignNumber(row.foreignAmounts().tax(),15,4,false);
        var net=row.type()==TradeType.BUY?gross.add(fee).add(tax):gross.subtract(fee).subtract(tax);
        if(net.signum()<0) throw new IllegalArgumentException("외화 수수료·세금이 매도금액보다 큽니다.");
        foreignNumber(net,15,4,false);
        var price=row.price();
        foreignNumber(price,15,4,true);
        try {
            var t=new AssetTrade(); t.setAssetPlan(plan);t.setInvestmentAsset(asset);t.setTradeDate(row.date());t.setTradeType(row.type());
            t.setQuantity(row.quantity());t.setUnitPrice(price);t.setCurrency("USD");t.setExchangeRate(null);
            t.setReferenceExchangeRate(row.foreignAmounts().sourceRate());
            t.setForeignGross(gross);t.setForeignFee(fee);t.setForeignTax(tax);t.setForeignSettlement(net);
            t.setFeeKrw(null);t.setTaxKrw(null);t.setSettlementAmountKrw(null);
            t.setImportKey(saveKey);t.setImportGroupKey(groupKey);t.setHistoryOnly(historyOnly(row,asset.getId(),existing));
            t.setMemo("나무 · "+row.description()+" · USD 원본 금액 · 원화 원가 미확정"+(t.isHistoryOnly()?" · 내역만 저장":""));
            t.setImportDetails("원본 종목: "+row.name()+" / 원본 단가: "+row.price()+" / 원본 거래·정산·수수료·세금: "+row.foreignAmounts()
                +" / 원본 거래일자: "+row.bookingDate()+" / 확인 종목: "+symbol+" USD / 확인 거래금액: "+gross+" / 확인 수수료: "+fee+" / 확인 세금: "+tax+" / 확인 정산금액: "+net+" / 매수 환율: 미입력");
            return t;
        } catch(ArithmeticException e) { throw new IllegalArgumentException("원화 환산 금액이 저장 범위를 초과합니다."); }
    }
    private BigDecimal foreignNumber(BigDecimal value,int integers,int fraction,boolean positive) {
        if(value==null || value.signum()<0 || (positive && value.signum()==0) || value.precision()-value.scale()>integers || Math.max(value.stripTrailingZeros().scale(),0)>fraction)
            throw new IllegalArgumentException("해외 금액의 자릿수와 양수를 확인하세요.");
        return value;
    }
    private long incomeDuplicateCount(NamuhTradeFileParser.Row row, Long planId, Long memberId) {
        return incomes.findByMemberMemberIdAndIncomeDateAndIncomeTypeAndAmount(memberId, row.date(), row.incomeType(), row.settlement()).stream()
                .filter(i -> i.getImportKey() == null || (Objects.equals(i.getSourcePlanId(), planId)
                        && Objects.equals(i.getSourceSecurityName(), row.name()) && Objects.equals(i.getSourceDescription(), row.description()))).count();
    }
    private void validateQuantities(List<AssetTrade> existing, List<AssetTrade> additions) {
        var affected = additions.stream().filter(t -> !t.isHistoryOnly()).map(t -> t.getInvestmentAsset().getId()).collect(Collectors.toSet());
        for (Long id : affected) {
            var events = new ArrayList<AssetTrade>();
            existing.stream().filter(t -> t.getInvestmentAsset().getId().equals(id) && !t.isHistoryOnly()).forEach(events::add);
            additions.stream().filter(t -> t.getInvestmentAsset().getId().equals(id) && !t.isHistoryOnly()).forEach(events::add);
            events.sort(Comparator.comparing(AssetTrade::getTradeDate).thenComparing(t -> t.getId() == null ? Long.MAX_VALUE : t.getId()));
            BigDecimal balance = BigDecimal.ZERO;
            for (var trade : events) {
                if (trade.getTradeType() == TradeType.SNAPSHOT) balance = trade.getQuantity();
                else balance = balance.add(trade.getTradeType() == TradeType.BUY ? trade.getQuantity() : trade.getQuantity().negate());
                if (balance.signum() < 0) throw new IllegalArgumentException("매도 수량이 보유 수량보다 많습니다. 이전 매수내역을 함께 가져오거나 현재 보유분을 먼저 등록한 뒤 다시 업로드하세요.");
            }
        }
    }
    private boolean historyOnly(NamuhTradeFileParser.Row row, Long assetId, List<AssetTrade> existing) {
        return existing.stream().anyMatch(t -> t.getInvestmentAsset().getId().equals(assetId)
                && t.getTradeType() == TradeType.SNAPSHOT && !t.getTradeDate().isBefore(row.date()));
    }
    private long tradeDuplicateCount(NamuhTradeFileParser.Row r,Long assetId,List<AssetTrade> existing,String groupKey) {
        return existing.stream().filter(t -> groupKey.equals(t.getImportKey()) || groupKey.equals(t.getImportGroupKey())
            || (t.getInvestmentAsset().getId().equals(assetId) && t.getTradeType()==r.type() && t.getTradeDate().equals(r.date())
                && t.getQuantity().compareTo(r.quantity())==0 && t.getUnitPrice().compareTo(r.price())==0
                && Objects.equals(t.getFeeKrw(),r.fee()) && Objects.equals(t.getTaxKrw(),r.tax()))).count();
    }
    private String confirmedKey(String groupKey) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((groupKey+"|CONFIRMED|"+UUID.randomUUID()).getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private String key(NamuhTradeFileParser.Row r, Long plan, Long member) {
        String value = String.join("|", r.overseas() ? "NAMUH_FOREIGN_V1" : r.transfer() ? "NAMUH_TRANSFER_V1" : r.income() ? "NAMUH_INCOME_V1" : "NAMUH_V1", member.toString(), plan.toString(), r.date().toString(), r.transfer() ? r.description() : r.income() ? r.incomeType().name() : (r.foreignFlow()?r.foreignAmounts().flowType():r.type().name()),
                r.name(), r.quantity().stripTrailingZeros().toPlainString(), r.price().stripTrailingZeros().toPlainString(),
                Long.toString(r.gross()), Long.toString(r.settlement()), Long.toString(r.fee()), Long.toString(r.tax()));
        if(r.overseas() && r.foreignAmounts().detailed()) value = String.join("|","NAMUH_USD_DETAIL_V1",member.toString(),plan.toString(),r.date().toString(),(r.foreignFlow()?r.foreignAmounts().flowType():r.type().name()),r.foreignAmounts().sourceCode()+(r.foreignFlow()?"|"+r.name():""),r.quantity().stripTrailingZeros().toPlainString(),r.price().stripTrailingZeros().toPlainString());
        if(r.overseas()) value += "|"+r.description()+"|"+r.foreignAmounts().gross().stripTrailingZeros().toPlainString()+"|"+r.foreignAmounts().settlement().stripTrailingZeros().toPlainString()+"|"+r.foreignAmounts().fee().stripTrailingZeros().toPlainString()+"|"+r.foreignAmounts().tax().stripTrailingZeros().toPlainString();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
