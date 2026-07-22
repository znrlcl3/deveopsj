package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.AssetPlan;

public interface AssetPlanRepository extends JpaRepository<AssetPlan, Long> {
	
	List<AssetPlan> findByMemberMemberId(Long memberId);

	Optional<AssetPlan> findByIdAndMemberMemberId(Long id, Long memberId);
	
}
