package com.deveopsj.spending.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.deveopsj.spending.entity.DailySpending;

public interface DailySpendingRepository extends JpaRepository<DailySpending, Long> {
    @Query("SELECT SUM(d.amount) FROM DailySpending d WHERE d.member.memberId = :memberId " +
           "AND d.spendingDate BETWEEN :startDate AND :endDate")
    Long getTotalSpending(@Param("memberId") Long memberId, 
                          @Param("startDate") LocalDate startDate, 
                          @Param("endDate") LocalDate endDate);
    
    List<DailySpending> findByMemberMemberIdAndSpendingDateBetween(Long memberId, LocalDate start, LocalDate end);
    
    List<DailySpending> findByMemberMemberIdOrderBySpendingDateDesc(Long memberId);

    Optional<DailySpending> findByIdAndMemberMemberId(Long id, Long memberId);
}
