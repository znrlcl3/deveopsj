package com.deveopsj.assetplan.repository;

import com.deveopsj.assetplan.entity.AssetBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AssetBalanceRepository extends JpaRepository<AssetBalance, Long> {
    List<AssetBalance> findByMemberIdOrderByIdDesc(Long memberId);
    Optional<AssetBalance> findByIdAndMemberId(Long id, Long memberId);
}
