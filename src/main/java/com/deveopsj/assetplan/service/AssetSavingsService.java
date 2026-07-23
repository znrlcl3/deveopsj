package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetSavingsService {

    private final AssetSavingsRepository assetSavingsRepository;
    private final AssetPlanRepository assetPlanRepository;

    public void save(AssetSavingsSaveRequest request, Member member) {
        AssetPlan assetPlan = assetPlanRepository.findByIdAndMemberMemberId(request.getAssetPlanId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));

        AssetSavings assetSavings = AssetSavings.builder()
                .assetPlan(assetPlan)
                .amount(request.getAmount())
                .depositDate(request.getDepositDate())
                .memo(request.getMemo() == null ? null : request.getMemo().trim())
                .build();
        assetSavingsRepository.save(assetSavings);
    }

    public List<AssetSavings> getSavingsByMember(Member member) {
        // AssetSavings 엔티티에는 member 필드가 없으므로, 
        // AssetPlan을 통해 member 조회 (JPQL 등이 필요할 수 있음)
        return assetSavingsRepository.findByAssetPlanMemberMemberId(member.getMemberId());
    }
}
