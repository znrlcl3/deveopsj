package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;
import com.deveopsj.spending.dto.*;
import com.deveopsj.spending.entity.*;
import com.deveopsj.spending.repository.*;

@SpringBootTest @ActiveProfiles("test")
@org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
class CardCancellationTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired CardCancellationService service;
    @Autowired DailySpendingRepository spending;
    @Autowired CardCancellationRepository history;
    @Autowired MemberRepository members;

    @Test
    void reviewRendersAndPostRequiresCsrfAndMatchingSessionToken() throws Exception {
        var owner = owner(); var original = original(owner); var preview = preview(owner, "web", -3000);
        var session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute("cardSpendingImportPreview", preview);
        var user = new com.deveopsj.member.security.CustomUserDetails(owner);
        var page = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/spending/card-cancellation")
                        .session(session).param("token", "token").param("rowIndex", "0")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("10,000원 → 취소 후 7,000원", "원거래가 속한 월", "아직 반영하지 않은 취소");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/spending/card-cancellation")
                        .session(session).param("token", "token").param("rowIndex", "0")
                        .param("targetId", original.getId().toString()).param("expectedAmount", "10000").param("reviewed", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/spending/card-cancellation")
                        .session(session).param("token", "wrong-token").param("rowIndex", "0")
                        .param("targetId", original.getId().toString()).param("expectedAmount", "10000").param("reviewed", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        assertThat(spending.findById(original.getId()).orElseThrow().getAmount()).isEqualTo(10000);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/spending/card-cancellation")
                        .session(session).param("token", "token").param("rowIndex", "0")
                        .param("targetId", original.getId().toString()).param("expectedAmount", "10000").param("reviewed", "true")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(user))
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().is3xxRedirection());
        assertThat(spending.findById(original.getId()).orElseThrow().getAmount()).isEqualTo(7000);
    }

    @Test
    void partialThenFullCancellationKeepsImportKeyAndReplayDoesNotDeductAgain() {
        var owner = owner(); var original = original(owner);
        var preview = preview(owner, "partial", -3000);
        assertThat(service.review(preview, 0, owner).candidates()).singleElement()
                .satisfies(c -> assertThat(c.afterAmount()).isEqualTo(7000));
        service.apply(preview, 0, owner, original.getId(), 10000, true);
        service.apply(preview, 0, owner, original.getId(), 10000, true);
        assertThat(spending.findById(original.getId()).orElseThrow().getAmount()).isEqualTo(7000);
        assertThat(service.review(preview, 0, owner).applied()).isNotNull();
        service.apply(preview(owner, "full", -7000), 0, owner, original.getId(), 7000, true);
        var zero = spending.findById(original.getId()).orElseThrow();
        assertThat(zero.getAmount()).isZero();
        assertThat(zero.getImportTransactionKey()).isEqualTo(original.getImportTransactionKey());
        assertThat(zero.getSpendingDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void staleAmountsWrongOwnersAndMissingConfirmationCannotChangeData() {
        var owner = owner(); var other = owner(); var original = original(owner);
        var preview = preview(owner, "protected", -3000);
        assertThatThrownBy(() -> service.apply(preview, 0, owner, original.getId(), 10000, false))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.review(preview, 0, other)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(preview(other, "other", -3000), 0, other, original.getId(), 10000, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(preview, 0, owner, original.getId(), 9999, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.apply(preview(owner, "excess", -10001), 0, owner, original.getId(), 10000, true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(spending.findById(original.getId()).orElseThrow().getAmount()).isEqualTo(10000);
        assertThat(history.findByMemberIdAndTransactionKey(owner.getMemberId(), "protected")).isEmpty();
    }

    @Test
    void concurrentRequestsForSameCancellationCannotDeductFromTwoPurchases() throws Exception {
        var owner = owner(); var first = original(owner); var second = original(owner);
        var preview = preview(owner, "race", -3000);
        var start = new java.util.concurrent.CountDownLatch(1);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (var target : List.of(first, second)) tasks.add(pool.submit(() -> {
                try { start.await(); service.apply(preview, 0, owner, target.getId(), 10000, true); }
                catch (org.springframework.dao.DataIntegrityViolationException expected) { /* unique key rolls back */ }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
            }));
            start.countDown();
            for (var task : tasks) task.get(20, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(spending.findById(first.getId()).orElseThrow().getAmount()
                    + spending.findById(second.getId()).orElseThrow().getAmount()).isEqualTo(17000);
            assertThat(history.findByMemberIdAndTransactionKey(owner.getMemberId(), "race")).isPresent();
        } finally { pool.shutdownNow(); }
    }

    private Member owner() {
        var m = new Member(); m.setLoginId("cancel-" + UUID.randomUUID());
        m.setPassword("test"); m.setName("취소 테스트"); m.setRole("USER");
        return members.saveAndFlush(m);
    }
    private DailySpending original(Member m) {
        return spending.saveAndFlush(DailySpending.builder().member(m).spendingDate(LocalDate.of(2026,1,1))
                .categoryCode("FOOD").amount(10000L).memo("테스트 가맹점").importSource("SHINHAN")
                .importTransactionKey(UUID.randomUUID().toString()).build());
    }
    private CardImportPreview preview(Member m, String key, long amount) {
        return new CardImportPreview("token", CardCompany.SHINHAN, "test.xlsx", List.of(
                new CardSpendingImportRow(12, LocalDate.of(2026,2,1), "테스트 가맹점", amount, key, "취소")), null, m.getMemberId());
    }
}
