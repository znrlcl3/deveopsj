package com.deveopsj.assetplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.deveopsj.assetplan.entity.AssetSavings;

import java.time.LocalDate;
import java.util.List;

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

    List<AssetSavings> findByAssetPlanMemberMemberId(Long memberId);
}
