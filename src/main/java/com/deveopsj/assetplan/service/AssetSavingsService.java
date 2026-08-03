package com.deveopsj.assetplan.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.dto.AssetSavingsUpdateRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;
import java.util.List;
import java.time.YearMonth;
import java.time.LocalDate;

@Service
@Transactional
@RequiredArgsConstructor
public class AssetSavingsService {

    private final AssetSavingsRepository assetSavingsRepository;
    private final AssetPlanRepository assetPlanRepository;
    private final GoalRepository goalRepository;

    public void save(AssetSavingsSaveRequest request, Member member) {
        DepositTarget target = resolveTarget(
                request.getDepositType(), request.getAssetPlanId(), request.getGoalId(), member);

        AssetSavings assetSavings = AssetSavings.builder()
                .assetPlan(target.assetPlan())
                .goal(target.goal())
                .depositType(request.getDepositType())
                .amount(request.getAmount())
                .depositDate(request.getDepositDate())
                .memo(request.getMemo() == null ? null : request.getMemo().trim())
                .build();
        assetSavingsRepository.save(assetSavings);
    }

    public List<AssetSavings> getSavingsByMember(Member member) {
        // AssetSavings 엔티티에는 member 필드가 없으므로, 
        // AssetPlan을 통해 member 조회 (JPQL 등이 필요할 수 있음)
        return assetSavingsRepository.findAllByMemberId(member.getMemberId());
    }

    @Transactional(readOnly = true)
    public List<AssetPlan> getDepositPlansByMember(Member member) {
        return assetPlanRepository.findByMemberMemberId(member.getMemberId());
    }

    @Transactional(readOnly = true)
    public List<AssetSavings> getSavingsByMemberAndMonth(Member member, YearMonth month) {
        return assetSavingsRepository
                .findAllByMemberIdAndDepositDateBetween(
                        member.getMemberId(), month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<AssetSavings> getSavingsByMemberAndPeriod(Member member, LocalDate start, LocalDate end) {
        return assetSavingsRepository.findAllByMemberIdAndDepositDateBetween(
                member.getMemberId(), start, end);
    }

    public void update(AssetSavingsUpdateRequest request, Member member) {
        AssetSavings savings = assetSavingsRepository
                .findByIdAndMemberId(request.getId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("수정할 수 있는 납입 내역이 없습니다."));
        DepositTarget target = resolveTarget(
                request.getDepositType(), request.getAssetPlanId(), request.getGoalId(), member);

        savings.setAssetPlan(target.assetPlan());
        savings.setGoal(target.goal());
        savings.setDepositType(request.getDepositType());
        savings.setAmount(request.getAmount());
        savings.setDepositDate(request.getDepositDate());
        savings.setMemo(request.getMemo() == null ? null : request.getMemo().trim());
    }

    public void deleteById(Long id, Member member) {
        AssetSavings savings = assetSavingsRepository
                .findByIdAndMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 수 있는 납입 내역이 없습니다."));
        assetSavingsRepository.delete(savings);
    }

    private DepositTarget resolveTarget(DepositType depositType, Long assetPlanId,
            Long goalId, Member member) {
        if (depositType == DepositType.PLAN) {
            AssetPlan assetPlan = assetPlanRepository
                    .findByIdAndMemberMemberId(assetPlanId, member.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));
            return new DepositTarget(assetPlan, assetPlan.getGoal());
        }
        if (depositType == DepositType.EXTRA) {
            Goal goal = goalRepository.findByIdAndMemberMemberId(goalId, member.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 목표를 사용할 수 없습니다."));
            return new DepositTarget(null, goal);
        }
        throw new IllegalArgumentException("납입 유형을 확인해 주세요.");
    }

    private record DepositTarget(AssetPlan assetPlan, Goal goal) {
    }
}
