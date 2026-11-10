package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.*;

@Service @RequiredArgsConstructor
public class AccountSummaryService {
    private final com.deveopsj.assetplan.repository.AccountTransferRepository transfers;
    private final com.deveopsj.assetplan.repository.ForeignAccountEntryRepository foreignEntries;
    private final AssetPlanRepository plans;
    private final AssetTradeRepository trades;
    private final IncomeRepository incomes;
    private final PortfolioValuationService portfolio;

    public record Account(Long id, String name, BigInteger buy, BigInteger sell,
            BigInteger dividend, BigInteger interest, BigInteger costs,
            BigInteger cost, BigInteger valuation, BigInteger profit, BigDecimal rate,
            int holdings, boolean stale, boolean valuationAvailable,BigInteger deposits,BigInteger withdrawals,
            BigDecimal usdBuy,BigDecimal usdSell,BigDecimal usdDividend,BigDecimal usdInterest,BigDecimal usdDeposits,BigDecimal usdWithdrawals,
            BigDecimal usdProfit,BigInteger fxProfit,boolean fxEstimated,List<com.deveopsj.assetplan.entity.ForeignAccountEntry> foreignRows) {
        public Account(Long id,String name,BigInteger buy,BigInteger sell,BigInteger dividend,BigInteger interest,BigInteger costs,BigInteger cost,BigInteger valuation,BigInteger profit,BigDecimal rate,int holdings,boolean stale,boolean valuationAvailable) {
            this(id,name,buy,sell,dividend,interest,costs,cost,valuation,profit,rate,holdings,stale,valuationAvailable,BigInteger.ZERO,BigInteger.ZERO,
                BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,null,profit,false,List.of());
        }
    }
    public record Summary(List<Account> accounts, boolean quoteFailed, BigInteger unassignedIncome) {
        public BigInteger totalValuation() {
            if (quoteFailed || accounts.stream().anyMatch(a -> !a.valuationAvailable() || a.valuation() == null)) return null;
            return accounts.stream().map(Account::valuation).reduce(BigInteger.ZERO, BigInteger::add);
        }
        public boolean staleValuation() { return accounts.stream().anyMatch(Account::stale); }
    }

    @Transactional(readOnly = true)
    public Summary summarize(Member member, YearMonth month) {
        var groups = new LinkedHashMap<Long, Totals>();
        plans.findByMemberMemberId(member.getMemberId()).stream()
                .sorted(Comparator.comparing(p -> p.getId()))
                .forEach(p -> groups.put(p.getId(), new Totals(p.getId(), p.getPlanName())));
        for (var trade : trades.findByAssetPlanMemberMemberIdAndTradeDateBetweenOrderByTradeDateDescIdDesc(
                member.getMemberId(), month.atDay(1), month.atEndOfMonth())) {
            var t = groups.get(trade.getAssetPlan().getId());
            if (t == null || trade.getTradeType() == TradeType.SNAPSHOT) continue;
            t.activity = true;
            if("USD".equals(trade.getCurrency())) {
                var amount=trade.getForeignSettlement()!=null ? trade.getForeignSettlement() : trade.getQuantity().multiply(trade.getUnitPrice());
                if(trade.getTradeType()==TradeType.BUY) t.usdBuy=t.usdBuy.add(amount);else t.usdSell=t.usdSell.add(amount);
            }
            if (trade.getTradeType() == TradeType.BUY) t.buy = addKnown(t.buy,trade.getSettlementAmountKrw());
            if (trade.getTradeType() == TradeType.SELL) t.sell = addKnown(t.sell,trade.getSettlementAmountKrw());
            t.costs = addKnown(addKnown(t.costs,trade.getFeeKrw()),trade.getTaxKrw());
        }
        BigInteger unassigned = BigInteger.ZERO;
        for (var income : incomes.findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
                member.getMemberId(), month.atDay(1), month.atEndOfMonth())) {
            if (income.getIncomeType() != IncomeType.DIVIDEND && income.getIncomeType() != IncomeType.INTEREST) continue;
            var t = groups.get(income.getSourcePlanId());
            if (t == null) { unassigned = unassigned.add(value(income.getAmount())); continue; }
            t.activity = true;
            if (income.getIncomeType() == IncomeType.DIVIDEND) t.dividend = t.dividend.add(value(income.getAmount()));
            else t.interest = t.interest.add(value(income.getAmount()));
            if(t.costs!=null) t.costs = t.costs.add(value(income.getSourceFee())).add(value(income.getSourceTax()));
        }
        for(var entry:transfers.findByAssetPlanMemberMemberIdAndTransferDateBetweenOrderByTransferDateDescIdDesc(member.getMemberId(),month.atDay(1),month.atEndOfMonth())) {
            var t=groups.get(entry.getAssetPlan().getId());if(t==null) continue;t.activity=true;
            if(entry.getType()==com.deveopsj.assetplan.entity.AccountTransfer.Type.DEPOSIT) t.deposits=t.deposits.add(value(entry.getAmount()));else t.withdrawals=t.withdrawals.add(value(entry.getAmount()));
        }
        for(var entry:foreignEntries.findByAssetPlanMemberMemberIdAndEntryDateBetweenOrderByEntryDateDescIdDesc(member.getMemberId(),month.atDay(1),month.atEndOfMonth())) {
            var t=groups.get(entry.getAssetPlan().getId());if(t==null) continue;t.activity=true;t.foreignRows.add(entry);
            switch(entry.getType()) { case DIVIDEND -> t.usdDividend=t.usdDividend.add(entry.getAmount());case INTEREST -> t.usdInterest=t.usdInterest.add(entry.getAmount());case DEPOSIT -> t.usdDeposits=t.usdDeposits.add(entry.getAmount());case WITHDRAWAL -> t.usdWithdrawals=t.usdWithdrawals.add(entry.getAmount()); }
        }
        boolean failed = false;
        try {
            for (var position : portfolio.getPortfolio(member).positions()) {
                var t = groups.get(position.assetPlanId());
                if (t == null) continue;
                t.holdings++;
                t.referenceCost=addKnown(t.referenceCost,position.referenceCostKrw());t.fxEstimated |= position.referenceCostEstimated();
                if("USD".equals(position.currency())) { t.usdHoldings++; if(position.nativeProfitLoss()==null) t.usdComplete=false;else t.usdProfit=t.usdProfit.add(position.nativeProfitLoss()); }
                t.cost = addKnown(t.cost,position.remainingCostKrw());
                t.stale |= position.staleQuote() || position.staleExchangeRate() || position.exchangeRateWarning() != null;
                if (position.valuationAmountKrw() == null || !position.quoteAvailable()) t.complete = false;
                else t.valuation = t.valuation.add(value(position.valuationAmountKrw()));
            }
        } catch (IllegalStateException | ArithmeticException e) { failed = true; }
        final boolean quoteFailed = failed;
        return new Summary(groups.values().stream().filter(t -> t.activity || t.holdings > 0 || quoteFailed)
                .map(t -> t.result(quoteFailed)).toList(), failed, unassigned);
    }
    @Transactional public void deleteForeignEntry(Long id,Long memberId) {
        incomes.lockOwner(memberId).orElseThrow(() -> new IllegalArgumentException("회원을 확인할 수 없습니다."));
        var entry=foreignEntries.findByIdAndAssetPlanMemberMemberId(id,memberId).orElseThrow(() -> new IllegalArgumentException("본인의 해외 내역만 삭제할 수 있습니다."));
        foreignEntries.delete(entry);
    }
    private static BigInteger addKnown(BigInteger a,Long b) { return a==null || b==null ? null : a.add(BigInteger.valueOf(b)); }
    private static BigInteger value(Long value) { return value == null ? BigInteger.ZERO : BigInteger.valueOf(value); }
    private static class Totals {
        final Long id; final String name;
        BigInteger buy=BigInteger.ZERO, sell=BigInteger.ZERO, dividend=BigInteger.ZERO, interest=BigInteger.ZERO,
                costs=BigInteger.ZERO, cost=BigInteger.ZERO, valuation=BigInteger.ZERO;
        BigInteger deposits=BigInteger.ZERO,withdrawals=BigInteger.ZERO,referenceCost=BigInteger.ZERO;
        BigDecimal usdBuy=BigDecimal.ZERO,usdSell=BigDecimal.ZERO,usdDividend=BigDecimal.ZERO,usdInterest=BigDecimal.ZERO,usdDeposits=BigDecimal.ZERO,usdWithdrawals=BigDecimal.ZERO,usdProfit=BigDecimal.ZERO;
        List<com.deveopsj.assetplan.entity.ForeignAccountEntry> foreignRows=new ArrayList<>();
        boolean activity, stale, complete=true,usdComplete=true,fxEstimated; int holdings,usdHoldings;
        Totals(Long id, String name) { this.id=id; this.name=name; }
        Account result(boolean failed) {
            boolean available = complete && !failed;
            BigInteger profit = available && cost!=null ? valuation.subtract(cost) : null;
            BigDecimal rate = available && cost!=null && cost.signum() > 0
                    ? new BigDecimal(profit).multiply(BigDecimal.valueOf(100)).divide(new BigDecimal(cost), 2, RoundingMode.HALF_UP) : null;
            return new Account(id, name, buy, sell, dividend, interest, costs, cost,
                    available ? valuation : null, profit, rate, holdings, stale, available,deposits,withdrawals,
                usdBuy,usdSell,usdDividend,usdInterest,usdDeposits,usdWithdrawals,usdHoldings>0 && usdComplete && !failed ? usdProfit:null,
                available && referenceCost!=null ? valuation.subtract(referenceCost):null,fxEstimated,List.copyOf(foreignRows));
        }
    }
}
