package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.entity.*;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class NamuhTradeImportService {
    private final NamuhTradeFileParser parser;
    private final AssetPlanRepository plans;
    private final AssetTradeRepository trades;
    private final InvestmentAssetRepository assets;
    private final com.deveopsj.income.repository.IncomeRepository incomes;
    public record Candidate(Long id, String label, boolean historyOnly, boolean duplicate) {}
    public record PreviewRow(NamuhTradeFileParser.Row source, List<Candidate> candidates, String reason) {
        public boolean eligible() { return reason.isEmpty() && candidates.stream().anyMatch(c -> !c.duplicate()); }
    }
    public record Preview(Long planId, String planName, List<PreviewRow> rows) {}
    public record Selection(int index, Long assetId) {}

    @Transactional(readOnly = true)
    public Preview preview(byte[] bytes, Long planId, Long memberId) {
        var plan = plans.findByIdAndMemberMemberId(planId, memberId)
                .orElseThrow(() -> new IllegalArgumentException("본인의 플랜을 선택해 주세요."));
        var rows = parser.parse(bytes);
        var existing = trades.findByAssetPlanIdOrderByTradeDateAscIdAsc(planId);
        var frequency = rows.stream().filter(NamuhTradeFileParser.Row::eligible)
                .collect(Collectors.groupingBy(r -> key(r, planId, memberId), Collectors.counting()));
        var result = new ArrayList<PreviewRow>();
        for (var row : rows) {
            if (!row.eligible()) { result.add(new PreviewRow(row, List.of(), row.error())); continue; }
            String key = key(row, planId, memberId);
            if (frequency.get(key) > 1) { result.add(new PreviewRow(row, List.of(), "파일 안에 동일한 거래가 여러 건 있습니다. 실제 별도 체결인지 확인 후 직접 등록하세요.")); continue; }
            if (row.income()) {
                String reason = incomes.existsByImportKey(key) ? "이미 가져온 수입입니다."
                        : duplicateIncome(row, planId, memberId) ? "같은 지급일·유형·입금액의 수입이 있습니다. 수입 관리에서 중복 여부를 확인하세요." : "";
                result.add(new PreviewRow(row, List.of(new Candidate(0L, "수입 등록 · " + row.incomeType().getLabel(), false, !reason.isEmpty())), reason));
                continue;
            }
            if (trades.existsByImportKey(key)) { result.add(new PreviewRow(row, List.of(), "이미 가져온 거래입니다.")); continue; }
            var candidates = assets.findByActiveTrueAndAssetNameAndCurrency(row.name(), "KRW").stream()
                    .filter(a -> Set.of("ETF", "KOSPI", "KOSDAQ").contains(a.getMarket()))
                    .map(a -> new Candidate(a.getId(), a.getAssetName() + " · " + a.getMarket() + " / " + a.getSymbol(),
                            historyOnly(row, a.getId(), existing), duplicate(row, a.getId(), existing))).toList();
            String reason = candidates.isEmpty() ? "연결할 국내 종목이 없습니다. 종목 목록을 등록·동기화한 뒤 다시 업로드하세요."
                    : candidates.stream().allMatch(Candidate::duplicate) ? "같은 날짜·종목·수량·단가·비용의 거래가 이미 있습니다." : "";
            result.add(new PreviewRow(row, candidates, reason));
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
        var incomeAdditions = new ArrayList<com.deveopsj.income.entity.Income>();
        for (var selection : selections) {
            if (!indices.add(selection.index()) || selection.index() < 0 || selection.index() >= preview.rows().size())
                throw new IllegalArgumentException("선택한 행이 올바르지 않습니다.");
            var view = preview.rows().get(selection.index()); var row = view.source();
            if (!view.eligible()) throw new IllegalArgumentException("저장할 수 없는 행입니다.");
            var candidate = view.candidates().stream().filter(c -> c.id().equals(selection.assetId()) && !c.duplicate()).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("미리보기의 종목을 선택하세요."));
            if (row.income()) {
                String incomeKey = key(row, plan.getId(), memberId);
                if (incomes.existsByImportKey(incomeKey) || duplicateIncome(row, plan.getId(), memberId))
                    throw new IllegalArgumentException("이미 등록되었거나 중복 의심 수입이 있습니다. 다시 업로드하세요.");
                var income = new com.deveopsj.income.entity.Income();
                income.setMember(owner); income.setIncomeDate(row.date()); income.setIncomeType(row.incomeType()); income.setAmount(row.settlement());
                income.setMemo("나무 · " + row.typeLabel() + (row.name().isBlank() ? "" : " · " + row.name()));
                income.setImportKey(incomeKey); income.setSourcePlanId(plan.getId()); income.setSourcePlanName(plan.getPlanName());
                income.setSourceSecurityName(row.name()); income.setSourceDescription(row.description()); income.setSourceBookingDate(row.bookingDate());
                income.setSourceGrossAmount(row.gross()); income.setSourceFee(row.fee()); income.setSourceTax(row.tax());
                incomeAdditions.add(income);
                continue;
            }
            var asset = assets.findByIdAndActiveTrue(candidate.id()).filter(a -> "KRW".equals(a.getCurrency())
                    && a.getAssetName().equalsIgnoreCase(row.name()) && Set.of("ETF", "KOSPI", "KOSDAQ").contains(a.getMarket()))
                    .orElseThrow(() -> new IllegalArgumentException("종목 정보가 변경되었습니다. 다시 업로드하세요."));
            String key = key(row, plan.getId(), memberId);
            if (trades.existsByImportKey(key) || duplicate(row, asset.getId(), existing))
                throw new IllegalArgumentException("이미 등록된 거래가 있습니다. 다시 업로드해 중복을 확인하세요.");
            boolean history = historyOnly(row, asset.getId(), existing);
            if (history != candidate.historyOnly()) throw new IllegalArgumentException("보유분 기준이 변경되었습니다. 다시 업로드하세요.");
            if (row.date().isAfter(LocalDate.now())) throw new IllegalArgumentException("거래일을 확인하세요.");
            var trade = new AssetTrade(); trade.setAssetPlan(plan); trade.setInvestmentAsset(asset);
            trade.setTradeDate(row.date()); trade.setTradeType(row.type()); trade.setQuantity(row.quantity());
            trade.setUnitPrice(row.price()); trade.setCurrency("KRW"); trade.setExchangeRate(BigDecimal.ONE);
            trade.setFeeKrw(row.fee()); trade.setTaxKrw(row.tax()); trade.setSettlementAmountKrw(row.settlement());
            trade.setImportKey(key); trade.setHistoryOnly(history); trade.setMemo("나무 · " + row.description() + (history ? " · 내역만 저장" : ""));
            additions.add(trade);
        }
        // Same-day source execution order is unavailable: put buys first, and disclose day-level precision.
        additions.sort(Comparator.comparing(AssetTrade::getTradeDate).thenComparing(t -> t.getTradeType() == TradeType.BUY ? 0 : 1));
        validateQuantities(existing, additions);
        trades.saveAllAndFlush(additions);
        incomes.saveAllAndFlush(incomeAdditions);
        return additions.size() + incomeAdditions.size();
    }
    private boolean duplicateIncome(NamuhTradeFileParser.Row row, Long planId, Long memberId) {
        return incomes.findByMemberMemberIdAndIncomeDateAndIncomeTypeAndAmount(memberId, row.date(), row.incomeType(), row.settlement()).stream()
                .anyMatch(i -> i.getImportKey() == null || (Objects.equals(i.getSourcePlanId(), planId)
                        && Objects.equals(i.getSourceSecurityName(), row.name()) && Objects.equals(i.getSourceDescription(), row.description())));
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
    private boolean duplicate(NamuhTradeFileParser.Row r, Long assetId, List<AssetTrade> existing) {
        return existing.stream().anyMatch(t -> t.getInvestmentAsset().getId().equals(assetId) && t.getTradeType() == r.type()
                && t.getTradeDate().equals(r.date()) && t.getQuantity().compareTo(r.quantity()) == 0
                && t.getUnitPrice().compareTo(r.price()) == 0 && t.getFeeKrw() == r.fee() && t.getTaxKrw() == r.tax());
    }
    private String key(NamuhTradeFileParser.Row r, Long plan, Long member) {
        String value = String.join("|", r.income() ? "NAMUH_INCOME_V1" : "NAMUH_V1", member.toString(), plan.toString(), r.date().toString(), r.income() ? r.incomeType().name() : r.type().name(),
                r.name(), r.quantity().stripTrailingZeros().toPlainString(), r.price().stripTrailingZeros().toPlainString(),
                Long.toString(r.gross()), Long.toString(r.settlement()), Long.toString(r.fee()), Long.toString(r.tax()));
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
