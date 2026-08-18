package com.deveopsj.assetplan.dto;

import java.time.LocalDate;

import com.deveopsj.assetplan.entity.RecurringSavings;

public record RecurringSavingsOccurrence(
        RecurringSavings rule,
        LocalDate scheduledDate,
        boolean confirmed) {
}
