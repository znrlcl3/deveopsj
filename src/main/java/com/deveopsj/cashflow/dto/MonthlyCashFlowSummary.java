package com.deveopsj.cashflow.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthlyCashFlowSummary(
        YearMonth selectedMonth,
        long totalIncomeKrw,
        long totalSpendingKrw,
        long totalDepositKrw,
        long remainingCashKrw,
        long securitiesDepositKrw,
        long totalBuyKrw,
        long totalSellKrw,
        long netPurchaseKrw,
        long uninvestedSecuritiesCashKrw,
        long estimatedSecuritiesAccountCashKrw,
        List<PlanCashFlow> planFlows,
        long consumptionKrw,
        long fixedExpenseKrw,
        long scheduledFixedKrw,
        long scheduledInstallmentKrw,
        long dividendIncomeKrw,
        long interestIncomeKrw,
        long futureSpendingKrw,
        int futureSpendingCount) {

    public long investmentIncomeKrw() {
        return Math.addExact(dividendIncomeKrw, interestIncomeKrw);
    }

    public long livingIncomeKrw() {
        return Math.subtractExact(totalIncomeKrw, investmentIncomeKrw());
    }

    public long incomeSpendingDifferenceKrw() {
        return Math.subtractExact(livingIncomeKrw(), totalSpendingKrw);
    }

    public long otherDepositKrw() {
        return Math.subtractExact(totalDepositKrw, securitiesDepositKrw);
    }

    public long scheduledTotalKrw() {
        return Math.addExact(scheduledFixedKrw, scheduledInstallmentKrw);
    }

    public long afterScheduledCashKrw() {
        return Math.subtractExact(Math.subtractExact(remainingCashKrw, futureSpendingKrw), scheduledTotalKrw());
    }
}
