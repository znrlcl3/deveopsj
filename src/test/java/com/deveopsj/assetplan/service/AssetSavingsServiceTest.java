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

import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetSavingsServiceTest {

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @InjectMocks
    private AssetSavingsService assetSavingsService;

    @Test
    void 본인의_자산플랜에만_적립한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        AssetSavingsSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));

        assetSavingsService.save(request, member);

        verify(assetSavingsRepository).save(org.mockito.ArgumentMatchers.argThat(savings ->
                savings.getAssetPlan() == plan && savings.getAmount().equals(100_000L)));
    }

    @Test
    void 타인의_자산플랜에는_적립하지_않는다() {
        Member member = member(7L);
        AssetSavingsSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetSavingsService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetSavingsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetSavingsSaveRequest request(Long assetPlanId) {
        AssetSavingsSaveRequest request = new AssetSavingsSaveRequest();
        request.setAssetPlanId(assetPlanId);
        request.setAmount(100_000L);
        request.setDepositDate(LocalDate.of(2026, 7, 22));
        request.setMemo("테스트");
        return request;
    }
}
