package com.deveopsj.spending.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.common.authorization.AuthorizationInput;
import com.deveopsj.common.authorization.PolicyAuthorizationService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.SpendingUpdateRequest;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SpendingService {

    private final DailySpendingRepository dailySpendingRepository;
    private final MasterCodeService masterCodeService;
    private final PolicyAuthorizationService policyAuthorizationService;

    @Transactional(readOnly = true)
    public List<DailySpending> getSpendings(Member member, LocalDate start, LocalDate end) {
        return dailySpendingRepository
                .findByMemberMemberIdAndSpendingDateBetweenOrderBySpendingDateDesc(
                        member.getMemberId(), start, end);
    }

    public void update(SpendingUpdateRequest request, Member member) {
        DailySpending spending = dailySpendingRepository
                .findByIdAndMemberMemberId(request.getId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("수정할 수 있는 지출 내역이 없습니다."));
        authorize(member, spending, "update");

        boolean activeCategory = masterCodeService.getActiveCodesByGroup("SPENDING_CAT").stream()
                .anyMatch(code -> code.getCodeId().equals(request.getCategory()));
        if (!activeCategory) {
            throw new IllegalArgumentException("사용할 수 없는 지출 카테고리입니다.");
        }

        spending.setSpendingDate(request.getDate());
        spending.setAmount(request.getAmount());
        spending.setMemo(request.getMemo().trim());
        spending.setCategoryCode(request.getCategory());
    }

    public void deleteById(Long id, Member member) {
        DailySpending spending = dailySpendingRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 수 있는 지출 내역이 없습니다."));
        authorize(member, spending, "delete");
        dailySpendingRepository.delete(spending);
    }

    private void authorize(Member member, DailySpending spending, String action) {
        policyAuthorizationService.authorize(new AuthorizationInput(
                new AuthorizationInput.Subject(member.getMemberId(), member.getRole()),
                action,
                new AuthorizationInput.Resource(
                        "spending", spending.getId(), spending.getMember().getMemberId())));
    }
}
