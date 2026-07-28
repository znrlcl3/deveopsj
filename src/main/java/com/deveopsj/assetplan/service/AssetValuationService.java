package com.deveopsj.assetplan.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetValuationSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetValuation;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetValuationRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetValuationService {

    private final AssetValuationRepository assetValuationRepository;
    private final AssetPlanRepository assetPlanRepository;

    public void save(AssetValuationSaveRequest request, Member member) {
        AssetPlan assetPlan = assetPlanRepository
                .findByIdAndMemberMemberId(request.getAssetPlanId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));

        assetValuationRepository.save(AssetValuation.builder()
                .assetPlan(assetPlan)
                .valuationAmount(request.getValuationAmount())
                .valuationDate(request.getValuationDate())
                .build());
    }

    @Transactional(readOnly = true)
    public List<AssetValuation> getValuationsByMember(Member member) {
        return assetValuationRepository
                .findByAssetPlanMemberMemberIdOrderByValuationDateDescIdDesc(member.getMemberId());
    }
}
