package com.deveopsj.spending.service;

import com.deveopsj.spending.dto.*;
import com.deveopsj.spending.entity.*;
import com.deveopsj.spending.repository.*;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Service @RequiredArgsConstructor
public class CardCancellationService {
    private final DailySpendingRepository spendings;
    private final CardCancellationRepository cancellations;
    private final com.deveopsj.common.authorization.PolicyAuthorizationService authorization;

    public CardSpendingImportRow cancellationRow(CardImportPreview preview, int index, Member member) {
        if (preview == null || !Objects.equals(preview.memberId(), member.getMemberId())
                || preview.cardCompany() != CardCompany.SHINHAN || index < 0 || index >= preview.rows().size())
            throw new IllegalArgumentException("본인의 신한카드 미리보기를 다시 열어 주세요.");
        var row = preview.rows().get(index);
        if (row.amount() >= 0 || row.amount() == Long.MIN_VALUE || row.installment() != null
                || row.merchantName().isBlank() || row.spendingDate().isAfter(java.time.LocalDate.now()))
            throw new IllegalArgumentException("확인 가능한 음수 취소 거래가 아닙니다.");
        return row;
    }

    @Transactional(readOnly = true)
    public Review review(CardImportPreview preview, int index, Member member) {
        var row = cancellationRow(preview, index, member);
        var applied = cancellations.findByMemberIdAndTransactionKey(member.getMemberId(), row.transactionKey()).orElse(null);
        var candidates = applied == null ? spendings.findCancellationCandidates(member.getMemberId(),
                row.merchantName().trim(), row.spendingDate(), -row.amount(), PageRequest.of(0, 20))
                .stream().map(s -> new Candidate(s.getId(), s.getSpendingDate(), s.getMemo(),
                        s.getCategoryCode(), s.getAmount(), s.getAmount() + row.amount())).toList() : List.<Candidate>of();
        return new Review(row, candidates, applied);
    }

    @Transactional
    public void apply(CardImportPreview preview, int index, Member member, Long targetId,
            long expectedAmount, boolean reviewed) {
        var row = cancellationRow(preview, index, member);
        if (!reviewed) throw new IllegalArgumentException("원거래와 취소 후 금액을 확인해 주세요.");
        if (cancellations.findByMemberIdAndTransactionKey(member.getMemberId(), row.transactionKey()).isPresent()) return;
        var target = spendings.findOwnedForCancellation(targetId, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("본인의 기존 지출만 선택할 수 있습니다."));
        authorization.authorize(new com.deveopsj.common.authorization.AuthorizationInput(
                new com.deveopsj.common.authorization.AuthorizationInput.Subject(member.getMemberId(), member.getRole()),
                "update", new com.deveopsj.common.authorization.AuthorizationInput.Resource(
                        "spending", targetId, target.getMember().getMemberId())));
        if (cancellations.findByMemberIdAndTransactionKey(member.getMemberId(), row.transactionKey()).isPresent()) return;
        if (!Objects.equals(target.getMemo() == null ? null : target.getMemo().trim(), row.merchantName().trim())
                || target.getSpendingDate().isAfter(row.spendingDate()) || target.getRecurringExpense() != null
                || (target.getImportSource() != null && !"SHINHAN".equals(target.getImportSource())))
            throw new IllegalArgumentException("취소 후보 조건과 일치하지 않는 지출입니다.");
        if (target.getAmount() != expectedAmount || expectedAmount < -row.amount())
            throw new IllegalArgumentException("원거래 금액이 바뀌었거나 취소액이 더 큽니다. 새 금액을 확인해 주세요.");
        long after = Math.addExact(expectedAmount, row.amount());
        cancellations.saveAndFlush(CardCancellation.builder().memberId(member.getMemberId())
                .transactionKey(row.transactionKey()).spendingId(targetId).refundAmount(-row.amount())
                .beforeAmount(expectedAmount).afterAmount(after).cancellationDate(row.spendingDate())
                .appliedAt(LocalDateTime.now()).build());
        target.setAmount(after);
        spendings.flush();
    }

    public record Candidate(Long id, java.time.LocalDate date, String memo, String category,
            long beforeAmount, long afterAmount) {}
    public record Review(CardSpendingImportRow row, List<Candidate> candidates, CardCancellation applied) {}
}
