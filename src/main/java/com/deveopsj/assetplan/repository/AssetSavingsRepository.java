package com.deveopsj.assetplan.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.deveopsj.assetplan.entity.AssetSavings;

import java.util.List;

public interface AssetSavingsRepository extends JpaRepository<AssetSavings, Long> {

    @Query("SELECT SUM(s.amount) FROM AssetSavings s WHERE s.assetPlan.id = :planId")
    Long getTotalSavingsByPlanId(@Param("planId") Long planId);

    List<AssetSavings> findByAssetPlanMemberMemberId(Long memberId);
}
