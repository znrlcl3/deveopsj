package com.deveopsj.assetplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.dto.RecurringSavingsRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.entity.RecurringSavings;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.assetplan.repository.RecurringSavingsRepository;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class RecurringSavingsServiceTest {

    @Mock RecurringSavingsRepository recurringSavingsRepository;
    @Mock AssetSavingsRepository assetSavingsRepository;
    @Mock AssetPlanRepository assetPlanRepository;
    @Mock GoalRepository goalRepository;

    @Test
    void 본인의_플랜으로_정기_납입을_등록한다() {
        Member member = member(7L);
        AssetPlan plan = plan(member);
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.of(plan));

        service().save(request(), member);

        ArgumentCaptor<RecurringSavings> captor = ArgumentCaptor.forClass(RecurringSavings.class);
        verify(recurringSavingsRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getAssetPlan()).isSameAs(plan);
        assertThat(captor.getValue().getGoal()).isSameAs(plan.getGoal());
        assertThat(captor.getValue().getName()).isEqualTo("청년적금 70");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void 타인의_플랜으로_정기_납입을_등록하지_않는다() {
        when(assetPlanRepository.findByIdAndMemberMemberId(3L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().save(request(), member(7L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(recurringSavingsRepository, never()).save(any());
    }

    @Test
    void 선택한_월의_정기_납입을_실제_납입으로_확정한다() {
        Member member = member(7L);
        RecurringSavings rule = rule(11L, member);
        when(recurringSavingsRepository.findByIdAndMemberMemberId(11L, 7L))
                .thenReturn(Optional.of(rule));
        when(assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(11L, "2026-02"))
                .thenReturn(false);

        service().confirm(11L, YearMonth.of(2026, 2), member);

        ArgumentCaptor<AssetSavings> captor = ArgumentCaptor.forClass(AssetSavings.class);
        verify(assetSavingsRepository).save(captor.capture());
        assertThat(captor.getValue().getDepositDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(captor.getValue().getRecurringSavings()).isSameAs(rule);
        assertThat(captor.getValue().getRecurringYearMonth()).isEqualTo("2026-02");
        assertThat(captor.getValue().getAmount()).isEqualTo(700_000L);
    }

    @Test
    void 같은_월에는_중복_확정하지_않는다() {
        Member member = member(7L);
        when(recurringSavingsRepository.findByIdAndMemberMemberId(11L, 7L))
                .thenReturn(Optional.of(rule(11L, member)));
        when(assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(11L, "2026-08"))
                .thenReturn(true);

        assertThatThrownBy(() -> service().confirm(11L, YearMonth.of(2026, 8), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 확정된 정기 납입입니다.");
        verify(assetSavingsRepository, never()).save(any());
    }

    @Test
    void 선택한_월의_미확정_정기_납입만_전체_확정한다() {
        Member member = member(7L);
        RecurringSavings first = rule(11L, member);
        RecurringSavings second = rule(12L, member);
        when(recurringSavingsRepository.findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(7L))
                .thenReturn(List.of(first, second));
        when(assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(11L, "2026-08"))
                .thenReturn(false);
        when(assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(12L, "2026-08"))
                .thenReturn(true);

        assertThat(service().confirmAll(YearMonth.of(2026, 8), member)).isEqualTo(1);
        ArgumentCaptor<List<AssetSavings>> captor = ArgumentCaptor.forClass(List.class);
        verify(assetSavingsRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(AssetSavings::getRecurringSavings).isSameAs(first);
    }

    @Test
    void 다른_사용자의_정기_납입은_변경하지_않는다() {
        when(recurringSavingsRepository.findByIdAndMemberMemberId(11L, 7L))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service().toggle(11L, member(7L)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private RecurringSavingsService service() {
        return new RecurringSavingsService(recurringSavingsRepository, assetSavingsRepository,
                assetPlanRepository, goalRepository);
    }

    private RecurringSavingsRequest request() {
        RecurringSavingsRequest request = new RecurringSavingsRequest();
        request.setName(" 청년적금 70 ");
        request.setDepositType(DepositType.PLAN);
        request.setAssetPlanId(3L);
        request.setAmount(700_000L);
        request.setPaymentDay(31);
        request.setStartDate(LocalDate.of(2026, 1, 1));
        return request;
    }

    private RecurringSavings rule(Long id, Member member) {
        AssetPlan plan = plan(member);
        return RecurringSavings.builder().id(id).member(member).name("청년적금 70")
                .assetPlan(plan).goal(plan.getGoal()).depositType(DepositType.PLAN)
                .amount(700_000L).paymentDay(31).startDate(LocalDate.of(2026, 1, 1))
                .active(true).build();
    }

    private AssetPlan plan(Member member) {
        Goal goal = new Goal();
        goal.setMember(member);
        AssetPlan plan = new AssetPlan();
        plan.setId(3L);
        plan.setMember(member);
        plan.setGoal(goal);
        return plan;
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }
}
