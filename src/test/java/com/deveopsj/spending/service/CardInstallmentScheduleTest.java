package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.*;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.repository.*;

class CardInstallmentScheduleTest {
    final DailySpendingRepository daily = mock(DailySpendingRepository.class);
    final RecurringExpenseRepository recurring = mock(RecurringExpenseRepository.class);
    final MasterCodeService codes = mock(MasterCodeService.class);
    final SpendingCategoryPredictionService prediction = mock(SpendingCategoryPredictionService.class);
    final Member member = new Member();
    final LocalDate start = LocalDate.of(2024, 1, 31);

    CardInstallmentScheduleTest() {
        member.setMemberId(7L);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
    }

    CardSpendingImportService service() {
        return new CardSpendingImportService(daily, codes, prediction, List.of(), recurring);
    }

    CardImportPreview preview() {
        var row = new CardSpendingImportRow(2, LocalDate.of(2024, 1, 5), "가맹점", 100000L, "key", null,
                "국내", List.of(), false, false, new CardInstallmentPlan(100000, 3, start));
        return new CardImportPreview("token", CardCompany.SAMSUNG, "sample.xlsx", List.of(row), "notice", 7L);
    }

    @Test
    void exactDivisionPreservesTotalAndClampsLeapYearMonthEnds() {
        var plan = new CardInstallmentPlan(100000, 3, start);
        assertThat(plan.proposedPayments()).extracting(CardInstallmentPlan.Payment::amount)
                .containsExactly(33333L, 33333L, 33334L);
        assertThat(plan.proposedPayments()).extracting(CardInstallmentPlan.Payment::date)
                .containsExactly(start, LocalDate.of(2024, 2, 29), LocalDate.of(2024, 3, 31));
        var large = new CardInstallmentPlan(Long.MAX_VALUE, 60, start);
        assertThat(large.proposedPayments().stream().map(p -> java.math.BigInteger.valueOf(p.amount()))
                .reduce(java.math.BigInteger.ZERO, java.math.BigInteger::add)).isEqualTo(java.math.BigInteger.valueOf(Long.MAX_VALUE));
        assertThatThrownBy(() -> new CardInstallmentPlan(2, 3, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CardInstallmentPlan(100, 61, start)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.payments(LocalDate.of(9999, 12, 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createsOneRulePerMonthAndNoActualSpending() {
        var result = service().confirm(preview(), List.of(0), Map.of(0, "FOOD"),
                Map.of(0, new CardInstallmentConfirmation(start, true)), member);
        assertThat(result.savedCount()).isZero();
        assertThat(result.scheduledCount()).isEqualTo(3);
        @SuppressWarnings("unchecked") var captor = ArgumentCaptor.forClass((Class<List<RecurringExpense>>)(Class<?>)List.class);
        verify(recurring).saveAll(captor.capture());
        assertThat(captor.getValue()).extracting(RecurringExpense::getAmount).containsExactly(33333L, 33333L, 33334L);
        assertThat(captor.getValue()).allSatisfy(r -> {
            assertThat(r.getMember()).isSameAs(member);
            assertThat(YearMonth.from(r.getStartDate())).isEqualTo(YearMonth.from(r.getEndDate()));
            assertThat(r.getMemo()).contains("수수료", "미포함");
        });
        assertThat(captor.getValue()).extracting(RecurringExpense::getCardInstallmentKey).doesNotHaveDuplicates();
        verify(daily, never()).save(any());
        verify(daily).saveAll(List.of());
    }

    @Test
    void reviewAndValidFirstDateAreRequiredBeforeAnyWrite() {
        for (var choice : List.of(new CardInstallmentConfirmation(start, false),
                new CardInstallmentConfirmation(null, true),
                new CardInstallmentConfirmation(LocalDate.of(2023, 12, 31), true))) {
            assertThatThrownBy(() -> service().confirm(preview(), List.of(0), Map.of(0, "FOOD"), Map.of(0, choice), member))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verify(recurring, never()).saveAll(any());
        verify(daily, never()).saveAll(any());
    }

    @Test
    void changingFirstDateDoesNotDuplicateAlreadyScheduledApproval() {
        when(recurring.existsByMemberMemberIdAndCardInstallmentKey(7L,
                CardSpendingImportService.installmentKey("key", 1))).thenReturn(true);
        var result = service().confirm(preview(), List.of(0), Map.of(0, "FOOD"),
                Map.of(0, new CardInstallmentConfirmation(start.plusMonths(1), true)), member);
        assertThat(result.duplicateCount()).isOne();
        assertThat(result.scheduledCount()).isZero();
        verify(recurring, never()).saveAll(any());
    }

    @Test
    void anotherMemberCannotConfirmPreview() {
        var other = new Member(); other.setMemberId(8L);
        assertThatThrownBy(() -> service().confirm(preview(), List.of(0), Map.of(0, "FOOD"),
                Map.of(0, new CardInstallmentConfirmation(start, true)), other)).hasMessageContaining("본인의");
        verifyNoInteractions(recurring);
    }
}
