package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.persistence.EntityManager;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.repository.MemberRepository;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SpendingImportPersistenceTest {
    @Autowired MemberRepository members;
    @Autowired DailySpendingRepository spendings;
    @Autowired EntityManager entityManager;

    @Test
    void registrationTimestampIsWrittenAndPreservedOnEdit() {
        Member owner = owner();
        var row = spendings.saveAndFlush(row(owner, "first-key"));
        entityManager.refresh(row);
        var created = row.getCreateDate();
        assertThat(created).isNotNull();
        row.setMemo("수정된 메모");
        spendings.saveAndFlush(row);
        entityManager.clear();
        assertThat(spendings.findById(row.getId()).orElseThrow().getCreateDate()).isEqualTo(created);
    }

    @Test
    void sameFileRowCannotBeInsertedTwiceForOneMember() {
        Member owner = owner();
        spendings.saveAndFlush(row(owner, "same-key"));
        assertThatThrownBy(() -> spendings.saveAndFlush(row(owner, "same-key")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void legitimateSimilarTransactionsAndOtherMembersAreAllowed() {
        Member first = owner();
        Member second = owner();
        spendings.saveAndFlush(row(first, "first-key"));
        spendings.saveAndFlush(row(first, "another-key"));
        spendings.saveAndFlush(row(second, "first-key"));
        assertThat(spendings.findByMemberMemberIdAndSpendingDateBetween(
                first.getMemberId(), LocalDate.of(2026, 8, 29), LocalDate.of(2026, 8, 29))).hasSize(2);
    }

    private Member owner() {
        SecurityContextHolder.clearContext();
        Member member = new Member();
        member.setLoginId("test-" + UUID.randomUUID());
        member.setName("테스트");
        member.setPassword("unused");
        member.setRole("USER");
        return members.saveAndFlush(member);
    }

    private DailySpending row(Member owner, String key) {
        return DailySpending.builder().member(owner).spendingDate(LocalDate.of(2026, 8, 29))
                .categoryCode("FOOD").amount(10000L).memo("점심")
                .importSource("EXCEL").importTransactionKey(key).build();
    }
}

