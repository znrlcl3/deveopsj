package com.deveopsj.assetplan.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.deveopsj.assetplan.entity.AssetPlan;

public interface AssetPlanRepository extends JpaRepository<AssetPlan, Long> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from AssetPlan p where p.id = :id and p.member.memberId = :memberId")
    Optional<AssetPlan> lockOwned(@org.springframework.data.repository.query.Param("id") Long id,
                                @org.springframework.data.repository.query.Param("memberId") Long memberId);
	
	List<AssetPlan> findByMemberMemberId(Long memberId);

	Optional<AssetPlan> findByIdAndMemberMemberId(Long id, Long memberId);
	
}
