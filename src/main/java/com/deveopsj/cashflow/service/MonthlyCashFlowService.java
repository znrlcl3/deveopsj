package com.deveopsj.cashflow.service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetTrade;
import com.deveopsj.assetplan.entity.AssetTrade.TradeType;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.AssetTradeRepository;
import com.deveopsj.cashflow.dto.MonthlyCashFlowSummary;
import com.deveopsj.cashflow.dto.PlanCashFlow;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MonthlyCashFlowService {

    private static final Set<String> SECURITIES_ASSET_TYPES =
            Set.of("STOCK", "ETF", "FUND", "SECURITIES");

    private final IncomeRepository incomeRepository;
    private final DailySpendingRepository dailySpendingRepository;
    private final AssetSavingsRepository assetSavingsRepository;
    private final AssetTradeRepository assetTradeRepository;

    public MonthlyCashFlowSummary getMonthlyCashFlow(Member member, YearMonth month) {
        var startDate = month.atDay(1);
        var endDate = month.atEndOfMonth();
        Long memberId = member.getMemberId();

        long income = valueOrZero(
                incomeRepository.getTotalIncome(memberId, startDate, endDate));
        long spending = valueOrZero(
                dailySpendingRepository.getTotalSpending(memberId, startDate, endDate));
        List<AssetSavings> savings = assetSavingsRepository
                .findAllByMemberIdAndDepositDateLessThanEqual(memberId, endDate);
        List<AssetTrade> trades = assetTradeRepository
                .findByAssetPlanMemberMemberIdAndTradeDateLessThanEqualOrderByTradeDateAscIdAsc(
                        memberId, endDate);

        long totalDeposit = savings.stream()
                .filter(saving -> !saving.getDepositDate().isBefore(startDate))
                .mapToLong(AssetSavings::getAmount)
                .reduce(0L, Math::addExact);
        long remainingCash = Math.subtractExact(
                Math.subtractExact(income, spending), totalDeposit);

        Map<Long, PlanAccumulator> planAccumulators = new LinkedHashMap<>();
        for (AssetSavings saving : savings) {
            if (saving.getAssetPlan() == null
                    || !isSecuritiesPlan(saving.getAssetPlan().getAssetType())) {
                continue;
            }
            PlanAccumulator accumulator = planAccumulators.computeIfAbsent(
                    saving.getAssetPlan().getId(),
                    ignored -> new PlanAccumulator(
                            saving.getAssetPlan().getId(),
                            planName(saving.getAssetPlan().getPlanName(),
                                    saving.getAssetPlan().getGoal().getTitle())));
            accumulator.cumulativeDeposit = Math.addExact(
                    accumulator.cumulativeDeposit, saving.getAmount());
            if (!saving.getDepositDate().isBefore(startDate)) {
                accumulator.deposit = Math.addExact(accumulator.deposit, saving.getAmount());
            }
        }
        for (AssetTrade trade : trades) {
            PlanAccumulator accumulator = planAccumulators.computeIfAbsent(
                    trade.getAssetPlan().getId(),
                    ignored -> new PlanAccumulator(
                            trade.getAssetPlan().getId(),
                            planName(trade.getAssetPlan().getPlanName(),
                                    trade.getAssetPlan().getGoal().getTitle())));
            if (trade.getTradeType() == TradeType.BUY) {
                accumulator.cumulativeBuy = Math.addExact(
                        accumulator.cumulativeBuy, trade.getSettlementAmountKrw());
                if (!trade.getTradeDate().isBefore(startDate)) {
                    accumulator.buy = Math.addExact(
                            accumulator.buy, trade.getSettlementAmountKrw());
                }
            } else {
                accumulator.cumulativeSell = Math.addExact(
                        accumulator.cumulativeSell, trade.getSettlementAmountKrw());
                if (!trade.getTradeDate().isBefore(startDate)) {
                    accumulator.sell = Math.addExact(
                            accumulator.sell, trade.getSettlementAmountKrw());
                }
            }
        }

        List<PlanCashFlow> planFlows = new ArrayList<>();
        long securitiesDeposit = 0;
        long totalBuy = 0;
        long totalSell = 0;
        long estimatedSecuritiesAccountCash = 0;
        for (PlanAccumulator accumulator : planAccumulators.values()) {
            PlanCashFlow planFlow = accumulator.toDto();
            planFlows.add(planFlow);
            securitiesDeposit = Math.addExact(securitiesDeposit, accumulator.deposit);
            totalBuy = Math.addExact(totalBuy, accumulator.buy);
            totalSell = Math.addExact(totalSell, accumulator.sell);
            estimatedSecuritiesAccountCash = Math.addExact(
                    estimatedSecuritiesAccountCash, planFlow.estimatedAccountCashKrw());
        }
        long netPurchase = Math.subtractExact(totalBuy, totalSell);
        long uninvestedSecuritiesCash = Math.subtractExact(
                securitiesDeposit, netPurchase);

        return new MonthlyCashFlowSummary(
                month, income, spending, totalDeposit, remainingCash,
                securitiesDeposit, totalBuy, totalSell, netPurchase,
                uninvestedSecuritiesCash, estimatedSecuritiesAccountCash,
                List.copyOf(planFlows));
    }

    private boolean isSecuritiesPlan(String assetType) {
        return assetType != null && SECURITIES_ASSET_TYPES.contains(assetType);
    }

    private String planName(String planName, String goalTitle) {
        return planName == null || planName.isBlank() ? goalTitle : planName;
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

    private static final class PlanAccumulator {

        private final Long assetPlanId;
        private final String planName;
        private long deposit;
        private long buy;
        private long sell;
        private long cumulativeDeposit;
        private long cumulativeBuy;
        private long cumulativeSell;

        private PlanAccumulator(Long assetPlanId, String planName) {
            this.assetPlanId = assetPlanId;
            this.planName = planName;
        }

        private PlanCashFlow toDto() {
            long netPurchase = Math.subtractExact(buy, sell);
            long cumulativeNetPurchase = Math.subtractExact(
                    cumulativeBuy, cumulativeSell);
            return new PlanCashFlow(
                    assetPlanId, planName, deposit, buy, sell,
                    netPurchase, Math.subtractExact(deposit, netPurchase),
                    cumulativeDeposit, cumulativeNetPurchase,
                    Math.subtractExact(cumulativeDeposit, cumulativeNetPurchase));
        }
    }
}
