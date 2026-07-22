package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.repository.AssetPlanRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import com.deveopsj.member.entity.Member;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetPlanService {

    private final AssetPlanRepository assetPlanRepository;

    public void save(AssetPlan assetPlan) {
        assetPlanRepository.save(assetPlan);
    }

    public List<AssetPlan> getPlansByMember(Member member) {
        return assetPlanRepository.findByMemberMemberId(member.getMemberId());
    }

    public void deleteById(Long id, Member member) {
        AssetPlan assetPlan = assetPlanRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 수 있는 자산 플랜이 없습니다."));
        assetPlanRepository.delete(assetPlan);
    }
}
