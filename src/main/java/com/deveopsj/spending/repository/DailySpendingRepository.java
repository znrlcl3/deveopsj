package com.deveopsj.spending.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Collection;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.deveopsj.spending.entity.DailySpending;

public interface DailySpendingRepository extends JpaRepository<DailySpending, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from DailySpending d where d.id = :id and d.member.memberId = :memberId")
    Optional<DailySpending> findOwnedForCancellation(@Param("id") Long id, @Param("memberId") Long memberId);

    @Query("select d from DailySpending d where d.member.memberId = :memberId "
            + "and trim(d.memo) = :merchant and d.spendingDate <= :date and d.amount >= :refund "
            + "and d.recurringExpense is null and (d.importSource = 'SHINHAN' or d.importSource is null) "
            + "order by d.spendingDate desc, d.id desc")
    List<DailySpending> findCancellationCandidates(@Param("memberId") Long memberId,
            @Param("merchant") String merchant, @Param("date") LocalDate date,
            @Param("refund") long refund, org.springframework.data.domain.Pageable pageable);
    @Query("SELECT SUM(d.amount) FROM DailySpending d WHERE d.member.memberId = :memberId " +
           "AND d.spendingDate BETWEEN :startDate AND :endDate")
    Long getTotalSpending(@Param("memberId") Long memberId, 
                          @Param("startDate") LocalDate startDate, 
                          @Param("endDate") LocalDate endDate);
    
    List<DailySpending> findByMemberMemberIdAndSpendingDateBetween(
            Long memberId, LocalDate start, LocalDate end);

    List<DailySpending> findByMemberMemberIdAndSpendingDateBetweenOrderBySpendingDateDesc(
            Long memberId, LocalDate start, LocalDate end);

    Optional<DailySpending> findByIdAndMemberMemberId(Long id, Long memberId);

    boolean existsByRecurringExpenseIdAndRecurringYearMonth(Long recurringExpenseId, String recurringYearMonth);

    boolean existsByMemberMemberIdAndImportSourceAndImportTransactionKey(
            Long memberId, String importSource, String importTransactionKey);

    List<DailySpending> findByMemberMemberIdAndImportSourceAndImportTransactionKeyIn(
            Long memberId, String importSource, Collection<String> transactionKeys);
}
