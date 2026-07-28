package com.deveopsj.assetplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetSavingsRepository extends JpaRepository<AssetSavings, Long> {

    @Query("""
            SELECT SUM(s.amount)
            FROM AssetSavings s
            WHERE s.assetPlan.id = :planId
              AND s.depositDate BETWEEN :startDate AND :endDate
            """)
    Long getTotalSavingsByPlanIdAndDepositDateBetween(
            @Param("planId") Long planId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT SUM(s.amount)
            FROM AssetSavings s
            WHERE s.assetPlan.id = :planId
            """)
    Long getTotalSavingsByPlanId(@Param("planId") Long planId);

    @Query("""
            SELECT s
            FROM AssetSavings s
            LEFT JOIN s.assetPlan p
            LEFT JOIN s.goal g
            WHERE p.member.memberId = :memberId
               OR g.member.memberId = :memberId
            ORDER BY s.depositDate DESC, s.id DESC
            """)
    List<AssetSavings> findAllByMemberId(@Param("memberId") Long memberId);

    @Query("""
            SELECT s
            FROM AssetSavings s
            LEFT JOIN s.assetPlan p
            LEFT JOIN s.goal g
            WHERE (p.member.memberId = :memberId
                   OR g.member.memberId = :memberId)
              AND s.depositDate BETWEEN :startDate AND :endDate
            ORDER BY s.depositDate DESC, s.id DESC
            """)
    List<AssetSavings> findAllByMemberIdAndDepositDateBetween(
            @Param("memberId") Long memberId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("""
            SELECT s
            FROM AssetSavings s
            LEFT JOIN s.assetPlan p
            LEFT JOIN s.goal g
            WHERE s.id = :id
              AND (p.member.memberId = :memberId
                   OR g.member.memberId = :memberId)
            """)
    Optional<AssetSavings> findByIdAndMemberId(
            @Param("id") Long id, @Param("memberId") Long memberId);

    @Query("""
            SELECT SUM(s.amount)
            FROM AssetSavings s
            WHERE s.goal.member.memberId = :memberId
              AND s.depositType = :depositType
              AND s.depositDate BETWEEN :startDate AND :endDate
            """)
    Long getTotalByMemberIdAndTypeAndDepositDateBetween(
            @Param("memberId") Long memberId,
            @Param("depositType") DepositType depositType,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);
}
