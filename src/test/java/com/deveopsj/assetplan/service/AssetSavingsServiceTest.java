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
import com.deveopsj.assetplan.dto.AssetSavingsUpdateRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AssetSavingsServiceTest {

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @Mock
    private GoalRepository goalRepository;

    @InjectMocks
    private AssetSavingsService assetSavingsService;

    @Test
    void 본인의_자산플랜에만_적립한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        plan.setAssetType("SAVINGS");
        plan.setGoal(new Goal());
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

    @Test
    void 주식형_플랜에도_실제계좌납입을_등록한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        plan.setAssetType("ETF");
        plan.setGoal(new Goal());
        AssetSavingsSaveRequest request = request(3L);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));

        assetSavingsService.save(request, member);

        verify(assetSavingsRepository).save(org.mockito.ArgumentMatchers.argThat(savings ->
                savings.getAssetPlan() == plan
                        && savings.getGoal() == plan.getGoal()
                        && savings.getDepositType() == DepositType.PLAN));
    }

    @Test
    void 기타추가납입은_본인의_목표에_직접_연결한다() {
        Member member = member(7L);
        Goal goal = new Goal();
        AssetSavingsSaveRequest request = request(null);
        request.setDepositType(DepositType.EXTRA);
        request.setGoalId(5L);
        when(goalRepository.findByIdAndMemberMemberId(5L, 7L)).thenReturn(Optional.of(goal));

        assetSavingsService.save(request, member);

        verify(assetSavingsRepository).save(org.mockito.ArgumentMatchers.argThat(savings ->
                savings.getAssetPlan() == null
                        && savings.getGoal() == goal
                        && savings.getDepositType() == DepositType.EXTRA));
    }

    @Test
    void 기타추가납입은_타인의_목표에_연결하지_않는다() {
        Member member = member(7L);
        AssetSavingsSaveRequest request = request(null);
        request.setDepositType(DepositType.EXTRA);
        request.setGoalId(5L);
        when(goalRepository.findByIdAndMemberMemberId(5L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetSavingsService.save(request, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetSavingsRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 본인의_납입내역만_수정한다() {
        Member member = member(7L);
        AssetPlan plan = new AssetPlan();
        plan.setAssetType("SAVINGS");
        AssetSavings savings = new AssetSavings();
        AssetSavingsUpdateRequest request = new AssetSavingsUpdateRequest();
        request.setId(9L);
        request.setAssetPlanId(3L);
        request.setDepositType(DepositType.PLAN);
        request.setAmount(150_000L);
        request.setDepositDate(LocalDate.of(2026, 7, 28));
        request.setMemo(" 수정 ");
        when(assetSavingsRepository.findByIdAndMemberId(9L, 7L))
                .thenReturn(Optional.of(savings));
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));

        assetSavingsService.update(request, member);

        org.assertj.core.api.Assertions.assertThat(savings.getAssetPlan()).isSameAs(plan);
        org.assertj.core.api.Assertions.assertThat(savings.getAmount()).isEqualTo(150_000L);
        org.assertj.core.api.Assertions.assertThat(savings.getMemo()).isEqualTo("수정");
    }

    @Test
    void 타인의_납입내역은_삭제하지_않는다() {
        Member member = member(7L);
        when(assetSavingsRepository.findByIdAndMemberId(9L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> assetSavingsService.deleteById(9L, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(assetSavingsRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }

    private AssetSavingsSaveRequest request(Long assetPlanId) {
        AssetSavingsSaveRequest request = new AssetSavingsSaveRequest();
        request.setAssetPlanId(assetPlanId);
        request.setDepositType(DepositType.PLAN);
        request.setAmount(100_000L);
        request.setDepositDate(LocalDate.of(2026, 7, 22));
        request.setMemo("테스트");
        return request;
    }
}
