package com.deveopsj.income.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.deveopsj.income.entity.Income;

public interface IncomeRepository extends JpaRepository<Income, Long> {

    List<Income> findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
            Long memberId, LocalDate startDate, LocalDate endDate);

    Optional<Income> findByIdAndMemberMemberId(Long id, Long memberId);

    @Query("""
            SELECT SUM(i.amount)
            FROM Income i
            WHERE i.member.memberId = :memberId
              AND i.incomeDate BETWEEN :startDate AND :endDate
            """)
    Long getTotalIncome(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
