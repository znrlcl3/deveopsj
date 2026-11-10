package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.repository.*;
import com.deveopsj.market.dto.PortfolioPosition;
import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class GoalValuationProgressService {
    private final AssetPlanRepository plans;
    private final AssetTradeRepository trades;
    private final AssetValuationRepository valuations;
    private final PortfolioValuationService portfolio;

    public record Progress(BigInteger amount, BigInteger remaining, BigDecimal percent,
            int missingPlans, int planCount, boolean stale, LocalDate oldestManualDate) {
        public boolean available() { return planCount > 0 && missingPlans == 0; }
    }
    @Transactional(readOnly=true)
    public Map<Long, Progress> summarize(Member member, List<Goal> goals) {
        var totals = new LinkedHashMap<Long, Total>();
        for (var goal : goals) if (owned(goal, member)) totals.put(goal.getId(), new Total(goal.getTargetAmount()));
        if (totals.isEmpty()) return Map.of();
        var ownedPlans = plans.findByMemberMemberId(member.getMemberId()).stream()
                .filter(p -> owned(p.getGoal(), member) && totals.containsKey(p.getGoal().getId())).toList();
        if (ownedPlans.isEmpty()) return results(totals);
        var securities = new HashSet<Long>();
        for (var trade : trades.findByAssetPlanMemberMemberIdOrderByTradeDateAscIdAsc(member.getMemberId())) {
            if (!trade.isHistoryOnly() || trade.getTradeType() == TradeType.SNAPSHOT) securities.add(trade.getAssetPlan().getId());
        }
        var positions = new HashMap<Long, List<PortfolioPosition>>();
        boolean failed = false;
        if (ownedPlans.stream().anyMatch(p -> securities.contains(p.getId()))) {
            try {
                for (var position : portfolio.getPortfolio(member).positions())
                    positions.computeIfAbsent(position.assetPlanId(), key -> new ArrayList<>()).add(position);
            } catch (IllegalStateException | ArithmeticException e) { failed = true; }
        }
        var latest = new HashMap<Long, com.deveopsj.assetplan.entity.AssetValuation>();
        for (var value : valuations.findByAssetPlanMemberMemberIdOrderByValuationDateDescIdDesc(member.getMemberId())) {
            if (value.getValuationDate() != null && !value.getValuationDate().isAfter(LocalDate.now())
                    && value.getValuationAmount() != null && value.getValuationAmount() >= 0)
                latest.putIfAbsent(value.getAssetPlan().getId(), value);
        }
        for (var plan : ownedPlans) {
            var total = totals.get(plan.getGoal().getId()); total.count++;
            if (securities.contains(plan.getId())) {
                var rows = positions.getOrDefault(plan.getId(), List.of());
                if (failed || rows.stream().anyMatch(p -> p.valuationAmountKrw() == null || !p.quoteAvailable())) {
                    total.missing++; continue;
                }
                for (var row : rows) {
                    total.amount = total.amount.add(BigInteger.valueOf(row.valuationAmountKrw()));
                    total.stale |= row.staleQuote() || row.staleExchangeRate() || row.exchangeRateWarning() != null;
                }
            } else {
                var value = latest.get(plan.getId());
                if (value == null) { total.missing++; continue; }
                total.amount = total.amount.add(BigInteger.valueOf(value.getValuationAmount()));
                if (total.date == null || value.getValuationDate().isBefore(total.date)) total.date=value.getValuationDate();
            }
        }
        return results(totals);
    }
    private static boolean owned(Goal goal, Member member) {
        return goal != null && goal.getMember() != null && Objects.equals(goal.getMember().getMemberId(), member.getMemberId());
    }
    private static Map<Long, Progress> results(Map<Long, Total> totals) {
        var result = new LinkedHashMap<Long, Progress>();
        totals.forEach((id,t) -> {
            boolean available=t.count>0 && t.missing==0;
            var remaining=BigInteger.valueOf(t.target).subtract(t.amount).max(BigInteger.ZERO);
            var percent=t.target>0 ? new BigDecimal(t.amount).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(t.target),1,RoundingMode.HALF_UP).min(BigDecimal.valueOf(100)) : BigDecimal.ZERO;
            result.put(id,new Progress(t.amount,available?remaining:null,available?percent:null,t.missing,t.count,t.stale,t.date));
        });
        return result;
    }
    private static class Total {
        final long target; BigInteger amount=BigInteger.ZERO; int count,missing; boolean stale; LocalDate date;
        Total(long target) { this.target=target; }
    }
}
