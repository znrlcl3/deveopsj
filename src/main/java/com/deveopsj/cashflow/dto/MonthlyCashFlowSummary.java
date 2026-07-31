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
        List<PlanCashFlow> planFlows) {
}
