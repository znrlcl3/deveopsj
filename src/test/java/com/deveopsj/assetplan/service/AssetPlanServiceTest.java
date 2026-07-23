package com.deveopsj.assetplan.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.dto.AssetPlanSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetPlanServiceTest {

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @Mock
    private GoalRepository goalRepository;

    @Mock
    private MasterCodeService masterCodeService;

    @InjectMocks
    private AssetPlanService assetPlanService;

    @Test
    void 본인의_목표로만_자산플랜을_저장한다() {
        Member member = member(7L);
        Goal goal = new Goal();
        AssetPlanSaveRequest request = request(3L);
        allowAssetType();
        when(goalRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(goal));

        assetPlanService.save(request, member);

        verify(assetPlanRepository).save(org.mockito.ArgumentMatchers.argThat(plan ->
                plan.getMember() == member && plan.getGoal() == goal));
    }

    @Test
    void 타인의_목표로는_자산플랜을_저장하지_않는다() {
        Member member = member(7L);
        AssetPlanSaveRequest request = request(3L);
        allowAssetType();
        when(goalRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetPlanService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetPlanRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 비활성_자산유형은_저장하지_않는다() {
        AssetPlanSaveRequest request = request(3L);
        when(masterCodeService.getActiveCodesByGroup("ASSET_TYPE")).thenReturn(java.util.List.of());

        assertThatThrownBy(() -> assetPlanService.save(request, member(7L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetPlanRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verify(goalRepository, never()).findByIdAndMemberMemberId(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 본인의_자산플랜만_삭제한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        when(assetPlanRepository.findByIdAndMemberMemberId(10L, 7L)).thenReturn(Optional.of(plan));

        assetPlanService.deleteById(10L, member);

        verify(assetPlanRepository).delete(plan);
    }

    @Test
    void 타인의_자산플랜은_삭제하지_않는다() {
        Member member = member(7L);
        when(assetPlanRepository.findByIdAndMemberMemberId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetPlanService.deleteById(10L, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetPlanRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetPlanSaveRequest request(Long goalId) {
        AssetPlanSaveRequest request = new AssetPlanSaveRequest();
        request.setGoalId(goalId);
        request.setAssetType("SAVINGS");
        request.setMonthlyAmount(100_000L);
        request.setMemo("테스트");
        return request;
    }

    private void allowAssetType() {
        when(masterCodeService.getActiveCodesByGroup("ASSET_TYPE")).thenReturn(java.util.List.of(
                MasterCodeDto.builder().codeId("SAVINGS").codeName("예적금").build()));
    }
}
