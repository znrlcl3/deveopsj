package com.deveopsj.dashboard.service;

import java.time.LocalDate;
import java.time.YearMonth;
import com.deveopsj.assetplan.service.HouseholdLedgerService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.service.RecurringExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardChecklistService {
    private final HouseholdLedgerService ledger;
    private final RecurringExpenseService expenses;

    public Checklist summarize(Member member, YearMonth month) {
        var pending = expenses.getOccurrences(member, month).stream().filter(o -> !o.confirmed()).toList();
        var rows = ledger.summary(member.getMemberId(), month).rows();
        var deposits = rows.stream().filter(r -> !r.plan().openingCoversMonth(month))
                .filter(r -> r.plan().getMonthlyAmount() > 0 && r.monthlyIn() == 0).toList();
        return new Checklist(pending.size(),
                pending.stream().filter(o -> !o.scheduledDate().isAfter(LocalDate.now())).count(),
                pending.stream().mapToLong(o -> o.rule().getAmount()).reduce(0L, Math::addExact),
                deposits.size(), deposits.stream().mapToLong(r -> r.plan().getMonthlyAmount()).reduce(0L, Math::addExact),
                rows.stream().filter(r -> r.plan().openingCoversMonth(month)).count());
    }

    public record Checklist(int pendingExpenses, long dueExpenses, long pendingExpenseAmount,
            int pendingDeposits, long plannedDepositAmount, long openingExcludedPlans) {}
}
