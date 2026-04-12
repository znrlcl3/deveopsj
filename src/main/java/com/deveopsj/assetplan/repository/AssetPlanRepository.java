package com.deveopsj.assetplan.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.AssetPlan;

public interface AssetPlanRepository extends JpaRepository<AssetPlan, Long> {
	
	List<AssetPlan> findByMemberMemberIdAndPlanDateBetween(Long memberId, LocalDate start, LocalDate end);
	
}