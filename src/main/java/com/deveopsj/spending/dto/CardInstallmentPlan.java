package com.deveopsj.spending.dto;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

/** An estimate of principal payments, never a card issuer's confirmed bill. */
public record CardInstallmentPlan(long totalAmount, int months, LocalDate firstPaymentDate)
        implements Serializable {
    public CardInstallmentPlan {
        if (months < 2 || months > 60 || totalAmount < months)
            throw new IllegalArgumentException("할부는 2~60개월이며 각 회차 금액이 1원 이상이어야 합니다.");
        if (firstPaymentDate != null && (firstPaymentDate.getYear() < 1900
                || firstPaymentDate.plusMonths(months - 1).getYear() > 9999))
            throw new IllegalArgumentException("첫 청구일과 할부 종료일을 확인해 주세요.");
    }

    public List<Payment> payments(LocalDate firstDate) {
        if (firstDate == null || firstDate.getYear() < 1900
                || firstDate.getYear() > 9999 || firstDate.plusMonths(months - 1).getYear() > 9999)
            throw new IllegalArgumentException("첫 청구일과 할부 종료일을 확인해 주세요.");
        long base = totalAmount / months;
        long remainder = totalAmount % months;
        return IntStream.range(0, months).mapToObj(i -> new Payment(i + 1,
                firstDate.plusMonths(i), base + (i == months - 1 ? remainder : 0))).toList();
    }

    public List<Payment> proposedPayments() {
        return firstPaymentDate == null ? List.of() : payments(firstPaymentDate);
    }

    public record Payment(int round, LocalDate date, long amount) implements Serializable {}
}
