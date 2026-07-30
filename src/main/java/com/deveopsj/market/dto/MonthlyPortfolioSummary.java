package com.deveopsj.market.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthlyPortfolioSummary(
        YearMonth selectedMonth,
        long monthlyBuyAmountKrw,
        long monthlySellAmountKrw,
        long monthlyNetInvestmentKrw,
        long monthEndCostKrw,
        List<MonthlyPortfolioPosition> positions) {
}
