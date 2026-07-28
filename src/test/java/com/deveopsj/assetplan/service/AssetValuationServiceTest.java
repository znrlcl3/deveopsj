package com.deveopsj.assetplan.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.dto.AssetValuationSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetValuationRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetValuationServiceTest {

    @Mock
    private AssetValuationRepository assetValuationRepository;

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @InjectMocks
    private AssetValuationService assetValuationService;

    @Test
    void 본인의_자산플랜에만_평가금액을_기록한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        AssetValuationSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));

        assetValuationService.save(request, member);

        verify(assetValuationRepository).save(org.mockito.ArgumentMatchers.argThat(valuation ->
                valuation.getAssetPlan() == plan
                        && valuation.getValuationAmount().equals(120_000L)
                        && valuation.getValuationDate().equals(LocalDate.of(2026, 7, 28))));
    }

    @Test
    void 타인의_자산플랜에는_평가금액을_기록하지_않는다() {
        Member member = member(7L);
        AssetValuationSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetValuationService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetValuationRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetValuationSaveRequest request(Long assetPlanId) {
        AssetValuationSaveRequest request = new AssetValuationSaveRequest();
        request.setAssetPlanId(assetPlanId);
        request.setValuationAmount(120_000L);
        request.setValuationDate(LocalDate.of(2026, 7, 28));
        return request;
    }
}
