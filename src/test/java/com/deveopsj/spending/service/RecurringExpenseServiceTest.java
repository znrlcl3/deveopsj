package com.deveopsj.spending.service;

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

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.RecurringExpenseRequest;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.repository.DailySpendingRepository;
import com.deveopsj.spending.repository.RecurringExpenseRepository;

@ExtendWith(MockitoExtension.class)
class RecurringExpenseServiceTest {

    @Mock RecurringExpenseRepository recurringExpenseRepository;
    @Mock DailySpendingRepository dailySpendingRepository;
    @Mock MasterCodeService masterCodeService;

    @Test
    void 선택한_월의_고정지출을_실제_지출로_확정한다() {
        RecurringExpenseService service = service();
        Member member = member(7L);
        RecurringExpense rule = rule(11L, member);
        when(recurringExpenseRepository.findByIdAndMemberMemberId(11L, 7L)).thenReturn(Optional.of(rule));
        when(dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(11L, "2026-02")).thenReturn(false);

        service.confirm(11L, YearMonth.of(2026, 2), member);

        ArgumentCaptor<DailySpending> captor = ArgumentCaptor.forClass(DailySpending.class);
        verify(dailySpendingRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().getSpendingDate()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(captor.getValue().getRecurringYearMonth()).isEqualTo("2026-02");
        assertThat(captor.getValue().getRecurringExpense()).isSameAs(rule);
    }

    @Test
    void 같은_월에는_중복_확정하지_않는다() {
        RecurringExpenseService service = service();
        Member member = member(7L);
        when(recurringExpenseRepository.findByIdAndMemberMemberId(11L, 7L)).thenReturn(Optional.of(rule(11L, member)));
        when(dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(11L, "2026-08")).thenReturn(true);

        assertThatThrownBy(() -> service.confirm(11L, YearMonth.of(2026, 8), member))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("이미 지출로 확정된 고정지출입니다.");
        verify(dailySpendingRepository, never()).save(any());
    }

    @Test
    void 다른_사용자의_고정지출은_변경하지_않는다() {
        RecurringExpenseService service = service();
        when(recurringExpenseRepository.findByIdAndMemberMemberId(11L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.toggle(11L, member(7L))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 규칙을_등록할_때_인증_사용자와_활성_카테고리를_사용한다() {
        RecurringExpenseService service = service();
        Member member = member(7L);
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT"))
                .thenReturn(List.of(MasterCodeDto.builder().codeId("HOME").codeName("주거").build()));

        service.save(request(), member);

        ArgumentCaptor<RecurringExpense> captor = ArgumentCaptor.forClass(RecurringExpense.class);
        verify(recurringExpenseRepository).save(captor.capture());
        assertThat(captor.getValue().getMember()).isSameAs(member);
        assertThat(captor.getValue().isActive()).isTrue();
        assertThat(captor.getValue().getName()).isEqualTo("월세");
    }

    @Test
    void 선택한_월의_미확정_고정지출만_전체_확정한다() {
        RecurringExpenseService service = service();
        Member member = member(7L);
        RecurringExpense unconfirmed = rule(11L, member);
        RecurringExpense confirmed = rule(12L, member);
        when(recurringExpenseRepository.findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(7L))
                .thenReturn(List.of(unconfirmed, confirmed));
        when(dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(11L, "2026-08"))
                .thenReturn(false);
        when(dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(12L, "2026-08"))
                .thenReturn(true);

        int count = service.confirmAll(YearMonth.of(2026, 8), member);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<List<DailySpending>> captor = ArgumentCaptor.forClass(List.class);
        verify(dailySpendingRepository).saveAll(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .extracting(DailySpending::getRecurringExpense)
                .isSameAs(unconfirmed);
    }

    private RecurringExpenseService service() {
        return new RecurringExpenseService(recurringExpenseRepository, dailySpendingRepository, masterCodeService);
    }

    private RecurringExpense rule(Long id, Member member) {
        return RecurringExpense.builder().id(id).member(member).name("월세").categoryCode("HOME")
                .amount(500_000L).paymentDay(31).startDate(LocalDate.of(2026, 1, 1)).active(true).build();
    }

    private RecurringExpenseRequest request() {
        RecurringExpenseRequest request = new RecurringExpenseRequest();
        request.setName(" 월세 ");
        request.setCategory("HOME");
        request.setAmount(500_000L);
        request.setPaymentDay(25);
        request.setStartDate(LocalDate.of(2026, 8, 1));
        return request;
    }

    private Member member(Long id) {
        Member member = new Member();
        member.setMemberId(id);
        return member;
    }
}
