package com.deveopsj.cashflow.service;

import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.cashflow.dto.MonthlyCashFlowSummary;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.income.entity.Income.IncomeType;
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
    private final com.deveopsj.spending.service.RecurringExpenseService recurringExpenseService;

    public MonthlyCashFlowSummary getMonthlyCashFlow(Member member, YearMonth month) {
        var startDate = month.atDay(1);
        var endDate = month.atEndOfMonth();
        Long memberId = member.getMemberId();

        long income = valueOrZero(
                incomeRepository.getTotalIncome(memberId, startDate, endDate));
        long dividend = incomeRepository.getTotalByType(memberId, IncomeType.DIVIDEND, startDate, endDate);
        long interest = incomeRepository.getTotalByType(memberId, IncomeType.INTEREST, startDate, endDate);
        long livingIncome = Math.subtractExact(income, Math.addExact(dividend, interest));
        var today = java.time.LocalDate.now();
        var monthlySpendings = dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(memberId, startDate, endDate);
        var futureSpendings = monthlySpendings.stream()
                .filter(s -> s.getSpendingDate() != null && s.getSpendingDate().isAfter(today)).toList();
        var spendings = monthlySpendings.stream()
                .filter(s -> s.getSpendingDate() == null || !s.getSpendingDate().isAfter(today)).toList();
        long futureSpending = futureSpendings.stream().mapToLong(s -> s.getAmount()).reduce(0L, Math::addExact);
        long spending = spendings.stream().mapToLong(s -> s.getAmount()).reduce(0L, Math::addExact);
        long fixed = spendings.stream().filter(s -> s.isFixedExpense())
                .mapToLong(s -> s.getAmount()).reduce(0L, Math::addExact);
        var pending = recurringExpenseService.getOccurrences(member, month).stream()
                .filter(o -> !o.confirmed()).toList();
        long scheduledFixed = pending.stream().filter(o -> o.rule().getCardInstallmentKey() == null)
                .mapToLong(o -> o.rule().getAmount()).reduce(0L, Math::addExact);
        long scheduledInstallment = pending.stream().filter(o -> o.rule().getCardInstallmentKey() != null)
                .mapToLong(o -> o.rule().getAmount()).reduce(0L, Math::addExact);
        List<AssetSavings> savings = assetSavingsRepository
                .findAllByMemberIdAndDepositDateLessThanEqual(memberId, endDate);
        long totalDeposit = savings.stream()
                .filter(saving -> !saving.getDepositDate().isBefore(startDate))
                .mapToLong(AssetSavings::getAmount)
                .reduce(0L, Math::addExact);
        long remainingCash = Math.subtractExact(
                Math.subtractExact(livingIncome, spending), totalDeposit);

        long securitiesDeposit = savings.stream()
                .filter(s -> !s.getDepositDate().isBefore(startDate) && s.getAssetPlan() != null
                        && isSecuritiesPlan(s.getAssetPlan().getAssetType()))
                .mapToLong(AssetSavings::getAmount).reduce(0L, Math::addExact);
        return new MonthlyCashFlowSummary(
                month, income, spending, totalDeposit, remainingCash,
                securitiesDeposit, 0, 0, 0,
                0, 0,
                List.of(), Math.subtractExact(spending, fixed), fixed,
                scheduledFixed, scheduledInstallment, dividend, interest, futureSpending, futureSpendings.size());
    }

    private boolean isSecuritiesPlan(String assetType) {
        return assetType != null && SECURITIES_ASSET_TYPES.contains(assetType);
    }

    private long valueOrZero(Long value) {
        return value == null ? 0 : value;
    }

}
