package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.AssetValuation;

public interface AssetValuationRepository extends JpaRepository<AssetValuation, Long> {

    Optional<AssetValuation> findTopByAssetPlanIdOrderByValuationDateDescIdDesc(Long planId);

    List<AssetValuation> findByAssetPlanMemberMemberIdOrderByValuationDateDescIdDesc(Long memberId);
}
