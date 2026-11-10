package com.deveopsj.market.dto;

import java.time.YearMonth;
import java.util.List;

public record MonthlyPortfolioSummary(
        YearMonth selectedMonth,
        Long monthlyBuyAmountKrw,
        Long monthlySellAmountKrw,
        Long monthlyNetInvestmentKrw,
        Long monthEndCostKrw,
        List<MonthlyPortfolioPosition> positions) {
}
