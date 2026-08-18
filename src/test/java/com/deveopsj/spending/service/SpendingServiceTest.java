package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.common.authorization.PolicyAuthorizationService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.SpendingUpdateRequest;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class SpendingServiceTest {

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private MasterCodeService masterCodeService;

    @Mock
    private PolicyAuthorizationService policyAuthorizationService;

    @InjectMocks
    private SpendingService spendingService;

    @Test
    void 본인의_지출만_삭제한다() {
        Member member = member(7L);
        DailySpending spending = spending(10L, member);
        when(dailySpendingRepository.findByIdAndMemberMemberId(10L, 7L)).thenReturn(Optional.of(spending));

        spendingService.deleteById(10L, member);

        verify(dailySpendingRepository).delete(spending);
    }

    @Test
    void 타인의_지출은_삭제하지_않는다() {
        Member member = member(7L);
        when(dailySpendingRepository.findByIdAndMemberMemberId(10L, 7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingService.deleteById(10L, member))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dailySpendingRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 로그인사용자의_선택기간_지출만_조회한다() {
        Member member = member(7L);
        LocalDate start = LocalDate.of(2026, 7, 1);
        LocalDate end = LocalDate.of(2026, 7, 31);
        List<DailySpending> expected = List.of(DailySpending.builder().amount(10_000L).build());
        when(dailySpendingRepository
                .findByMemberMemberIdAndSpendingDateBetweenOrderBySpendingDateDesc(7L, start, end))
                .thenReturn(expected);

        assertThat(spendingService.getSpendings(member, start, end)).isSameAs(expected);
    }

    @Test
    void 본인의_지출만_수정한다() {
        Member member = member(7L);
        DailySpending spending = DailySpending.builder()
                .id(10L)
                .member(member)
                .amount(10_000L)
                .memo("수정 전")
                .categoryCode("FOOD")
                .spendingDate(LocalDate.of(2026, 7, 1))
                .build();
        SpendingUpdateRequest request = updateRequest(10L);
        when(dailySpendingRepository.findByIdAndMemberMemberId(10L, 7L))
                .thenReturn(Optional.of(spending));
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("TRANS").codeName("교통").build()));

        spendingService.update(request, member);

        assertThat(spending.getAmount()).isEqualTo(20_000L);
        assertThat(spending.getMemo()).isEqualTo("버스");
        assertThat(spending.getCategoryCode()).isEqualTo("TRANS");
        assertThat(spending.getSpendingDate()).isEqualTo(LocalDate.of(2026, 7, 2));
    }

    @Test
    void 타인의_지출은_수정하지_않는다() {
        SpendingUpdateRequest request = updateRequest(10L);
        when(dailySpendingRepository.findByIdAndMemberMemberId(10L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> spendingService.update(request, member(7L)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(masterCodeService, never()).getActiveCodesByGroup("SPENDING_CAT");
    }

    @Test
    void OPA가_거부하면_본인의_지출도_삭제하지_않는다() {
        Member member = member(7L);
        DailySpending spending = spending(10L, member);
        when(dailySpendingRepository.findByIdAndMemberMemberId(10L, 7L))
                .thenReturn(Optional.of(spending));
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(policyAuthorizationService).authorize(org.mockito.ArgumentMatchers.any());

        assertThatThrownBy(() -> spendingService.deleteById(10L, member))
                .isInstanceOf(AccessDeniedException.class);
        verify(dailySpendingRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        member.setRole("USER");
        return member;
    }

    private DailySpending spending(Long id, Member member) {
        return DailySpending.builder().id(id).member(member).build();
    }

    private SpendingUpdateRequest updateRequest(Long id) {
        SpendingUpdateRequest request = new SpendingUpdateRequest();
        request.setId(id);
        request.setDate(LocalDate.of(2026, 7, 2));
        request.setAmount(20_000L);
        request.setMemo(" 버스 ");
        request.setCategory("TRANS");
        return request;
    }
}
