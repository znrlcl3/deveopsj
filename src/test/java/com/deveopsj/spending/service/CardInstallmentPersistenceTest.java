package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;
import com.deveopsj.spending.dto.*;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.repository.*;

@SpringBootTest
@ActiveProfiles("test")
class CardInstallmentPersistenceTest {
    @Autowired CardSpendingImportService imports;
    @Autowired RecurringExpenseService recurring;
    @Autowired MemberRepository members;
    @Autowired RecurringExpenseRepository rules;
    @Autowired DailySpendingRepository daily;
    @MockitoBean MasterCodeService codes;
    @MockitoBean SpendingCategoryPredictionService prediction;

    @BeforeEach
    void categories() {
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
    }

    @Test
    void schedulingIsSeparateFromActualsAndEachRoundCanBeCorrectedBeforeConfirmation() {
        var owner = owner();
        var first = LocalDate.of(2024, 1, 31);
        var result = register(owner, "schedule", first);
        assertThat(result.scheduledCount()).isEqualTo(3);
        assertThat(actuals(owner)).hasSize(1); // Only the ordinary purchase.
        var occurrence = recurring.getOccurrences(owner, YearMonth.of(2024, 2)).get(0);
        assertThat(occurrence.scheduledDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        var rule = occurrence.rule();
        var request = new RecurringExpenseRequest();
        request.setId(rule.getId()); request.setName(rule.getName()); request.setCategory("FOOD");
        request.setAmount(35000L); request.setPaymentDay(31);
        request.setStartDate(rule.getStartDate()); request.setEndDate(rule.getEndDate()); request.setMemo(rule.getMemo());
        recurring.update(request, owner);
        recurring.confirm(rule.getId(), YearMonth.of(2024, 2), owner);
        assertThat(actuals(owner)).hasSize(2).anySatisfy(s -> {
            assertThat(s.getAmount()).isEqualTo(35000L);
            assertThat(s.getSpendingDate()).isEqualTo(LocalDate.of(2024, 2, 29));
        });
        assertThatThrownBy(() -> recurring.confirm(rule.getId(), YearMonth.of(2024, 2), owner))
                .hasMessageContaining("이미");
        assertThatThrownBy(() -> recurring.update(request, owner)).hasMessageContaining("확정된");
        assertThatThrownBy(() -> register(owner, "schedule", first.plusMonths(1)))
                .hasMessageContaining("중복 후보");
        assertThat(rules.findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(owner.getMemberId())).hasSize(3);
    }

    @Test
    void futureInstallmentsStayPlannedAndOwnerCannotBeSpoofed() {
        var owner = owner();
        var first = LocalDate.now().plusMonths(3);
        register(owner, "future", first);
        var month = YearMonth.from(first);
        var occurrence = recurring.getOccurrences(owner, month).get(0);
        assertThatThrownBy(() -> recurring.confirm(occurrence.rule().getId(), month, owner))
                .hasMessageContaining("미래");
        assertThat(recurring.confirmAll(month, owner)).isZero();
        assertThat(actuals(owner)).hasSize(1);
        assertThatThrownBy(() -> recurring.confirm(occurrence.rule().getId(), month, owner()))
                .hasMessageContaining("찾을 수 없습니다");
    }

    @Test
    void databaseUniqueCollisionRollsBackOrdinaryPurchaseAndEveryNewRound() {
        var owner = owner();
        var first = LocalDate.of(2024, 1, 31);
        rules.saveAndFlush(RecurringExpense.builder().member(owner).name("기존 회차").categoryCode("FOOD")
                .amount(33333L).paymentDay(31).startDate(first.withDayOfMonth(1)).endDate(first)
                .active(true).cardInstallmentKey(CardSpendingImportService.installmentKey("collision", 2)).build());
        assertThatThrownBy(() -> register(owner, "collision", first)).hasMessageContaining("이미 등록");
        assertThat(actuals(owner)).isEmpty();
        assertThat(rules.findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(owner.getMemberId())).hasSize(1);
        assertThat(rules.existsByMemberMemberIdAndCardInstallmentKey(owner.getMemberId(),
                CardSpendingImportService.installmentKey("collision", 1))).isFalse();
        // The same card identifier is allowed in a different member's ledger.
        assertThat(register(owner(), "collision", first).scheduledCount()).isEqualTo(3);
    }

    private CardSpendingImportService.ImportResult register(Member owner, String key, LocalDate first) {
        var installment = new CardSpendingImportRow(2, LocalDate.of(2023, 12, 15), "할부 가맹점", 100000,
                key, null, "국내", List.of(), false, false, new CardInstallmentPlan(100000, 3, first));
        var ordinary = new CardSpendingImportRow(3, LocalDate.of(2024, 1, 1), "일시불", 4000,
                key + "-ordinary", null);
        var preview = new CardImportPreview("token", CardCompany.SAMSUNG, "synthetic.xlsx",
                List.of(installment, ordinary), null, owner.getMemberId());
        return imports.confirm(preview, List.of(0, 1), Map.of(0, "FOOD", 1, "FOOD"),
                Map.of(0, new CardInstallmentConfirmation(first, true)), owner);
    }

    private java.util.List<com.deveopsj.spending.entity.DailySpending> actuals(Member owner) {
        return daily.findByMemberMemberIdAndSpendingDateBetween(owner.getMemberId(),
                LocalDate.of(2023, 1, 1), LocalDate.now().plusYears(10));
    }

    private Member owner() {
        var owner = new Member(); owner.setLoginId("installment-" + UUID.randomUUID());
        owner.setName("테스트"); owner.setPassword("unused"); owner.setRole("USER");
        return members.saveAndFlush(owner);
    }
}
