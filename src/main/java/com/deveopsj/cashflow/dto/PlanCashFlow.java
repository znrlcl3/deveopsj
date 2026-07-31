package com.deveopsj.cashflow.dto;

public record PlanCashFlow(
        Long assetPlanId,
        String planName,
        long depositAmountKrw,
        long buyAmountKrw,
        long sellAmountKrw,
        long netPurchaseAmountKrw,
        long uninvestedAmountKrw,
        long cumulativeDepositAmountKrw,
        long cumulativeNetPurchaseAmountKrw,
        long estimatedAccountCashKrw) {
}
