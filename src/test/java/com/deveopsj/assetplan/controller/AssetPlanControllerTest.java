package com.deveopsj.assetplan.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetPlanControllerTest {

    @Mock
    private AssetPlanService assetPlanService;

    @Mock
    private MasterCodeService masterCodeService;

    @Mock
    private GoalService goalService;

    @InjectMocks
    private AssetPlanController assetPlanController;

    @Test
    void 등록화면은_등록에_필요한_값만_조회한다() {
        Member member = member();
        ExtendedModelMap model = new ExtendedModelMap();
        when(masterCodeService.getAllActiveCodesGrouped()).thenReturn(Map.of());
        when(goalService.getGoalsByMember(member)).thenReturn(List.of());

        String view = assetPlanController.assetPlanForm(model, member);

        assertThat(view).isEqualTo("assetplan/form");
        assertThat(model).containsKeys("codeMap", "goals");
        assertThat(model).doesNotContainKey("plans");
        verify(assetPlanService, never()).getPlansByMember(member);
    }

    @Test
    void 내역화면은_로그인사용자의_플랜과_목표를_조회한다() {
        Member member = member();
        ExtendedModelMap model = new ExtendedModelMap();
        List<AssetPlan> plans = List.of(new AssetPlan());
        List<Goal> goals = List.of(new Goal());
        when(assetPlanService.getPlansByMember(member)).thenReturn(plans);
        when(goalService.getGoalsByMember(member)).thenReturn(goals);

        String view = assetPlanController.assetPlanList(model, member);

        assertThat(view).isEqualTo("assetplan/list");
        assertThat(model.get("plans")).isSameAs(plans);
        assertThat(model.get("goals")).isSameAs(goals);
        verify(assetPlanService).getPlansByMember(member);
    }

    private Member member() {
        Member member = new Member();
        member.setMemberId(7L);
        return member;
    }
}
