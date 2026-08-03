package com.deveopsj.spending.dto;

import java.time.LocalDate;

import com.deveopsj.spending.entity.RecurringExpense;

public record RecurringExpenseOccurrence(
        RecurringExpense rule,
        LocalDate scheduledDate,
        boolean confirmed) {
}
