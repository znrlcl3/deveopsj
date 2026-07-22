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

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetPlanServiceTest {

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @InjectMocks
    private AssetPlanService assetPlanService;

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
}
