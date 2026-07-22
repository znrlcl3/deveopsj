package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetPlanSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.GoalRepository;

import lombok.RequiredArgsConstructor;

import java.util.List;
import com.deveopsj.member.entity.Member;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetPlanService {

    private final AssetPlanRepository assetPlanRepository;
    private final GoalRepository goalRepository;

    public void save(AssetPlanSaveRequest request, Member member) {
        Goal goal = goalRepository.findByIdAndMemberMemberId(request.getGoalId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("선택한 목표를 사용할 수 없습니다."));

        AssetPlan assetPlan = new AssetPlan();
        assetPlan.setMember(member);
        assetPlan.setGoal(goal);
        assetPlan.setAssetType(request.getAssetType());
        assetPlan.setMonthlyAmount(request.getMonthlyAmount());
        assetPlan.setMemo(request.getMemo());
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
