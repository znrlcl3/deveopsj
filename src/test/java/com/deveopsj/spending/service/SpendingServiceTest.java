package com.deveopsj.spending.service;

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

import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class SpendingServiceTest {

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @InjectMocks
    private SpendingService spendingService;

    @Test
    void 본인의_지출만_삭제한다() {
        Member member = member(7L);
        DailySpending spending = DailySpending.builder().build();
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

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }
}
