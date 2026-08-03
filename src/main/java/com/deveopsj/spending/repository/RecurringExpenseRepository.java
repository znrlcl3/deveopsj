package com.deveopsj.spending.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.spending.entity.RecurringExpense;

public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, Long> {

    List<RecurringExpense> findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(Long memberId);

    Optional<RecurringExpense> findByIdAndMemberMemberId(Long id, Long memberId);
}
