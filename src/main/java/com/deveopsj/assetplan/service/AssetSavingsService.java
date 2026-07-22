package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetSavingsService {

    private final AssetSavingsRepository assetSavingsRepository;

    @Transactional
    public void save(AssetSavings assetSavings) {
        assetSavingsRepository.save(assetSavings);
    }

    public List<AssetSavings> getSavingsByMember(Member member) {
        // AssetSavings 엔티티에는 member 필드가 없으므로, 
        // AssetPlan을 통해 member 조회 (JPQL 등이 필요할 수 있음)
        return assetSavingsRepository.findByAssetPlanMemberMemberId(member.getMemberId());
    }
}
