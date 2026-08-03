package com.deveopsj.spending.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.RecurringExpenseOccurrence;
import com.deveopsj.spending.dto.RecurringExpenseRequest;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.entity.RecurringExpense;
import com.deveopsj.spending.repository.DailySpendingRepository;
import com.deveopsj.spending.repository.RecurringExpenseRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringExpenseRepository;
    private final DailySpendingRepository dailySpendingRepository;
    private final MasterCodeService masterCodeService;

    @Transactional(readOnly = true)
    public List<RecurringExpense> getRules(Member member) {
        return recurringExpenseRepository
                .findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(member.getMemberId());
    }

    public void save(RecurringExpenseRequest request, Member member) {
        validateCategory(request.getCategory());
        recurringExpenseRepository.save(RecurringExpense.builder()
                .member(member)
                .name(request.getName().trim())
                .categoryCode(request.getCategory())
                .amount(request.getAmount())
                .paymentDay(request.getPaymentDay())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(true)
                .memo(trimToNull(request.getMemo()))
                .build());
    }

    public void update(RecurringExpenseRequest request, Member member) {
        RecurringExpense rule = ownedRule(request.getId(), member);
        validateCategory(request.getCategory());
        rule.setName(request.getName().trim());
        rule.setCategoryCode(request.getCategory());
        rule.setAmount(request.getAmount());
        rule.setPaymentDay(request.getPaymentDay());
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setMemo(trimToNull(request.getMemo()));
    }

    public void toggle(Long id, Member member) {
        RecurringExpense rule = ownedRule(id, member);
        rule.setActive(!rule.isActive());
    }

    @Transactional(readOnly = true)
    public List<RecurringExpenseOccurrence> getOccurrences(Member member, YearMonth month) {
        String yearMonth = month.toString();
        return getRules(member).stream()
                .filter(rule -> appliesTo(rule, month))
                .map(rule -> new RecurringExpenseOccurrence(
                        rule,
                        month.atDay(Math.min(rule.getPaymentDay(), month.lengthOfMonth())),
                        dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(
                                rule.getId(), yearMonth)))
                .toList();
    }

    public void confirm(Long id, YearMonth month, Member member) {
        RecurringExpense rule = ownedRule(id, member);
        if (!appliesTo(rule, month)) {
            throw new IllegalArgumentException("해당 월에 적용되는 고정지출이 아닙니다.");
        }
        String yearMonth = month.toString();
        if (dailySpendingRepository.existsByRecurringExpenseIdAndRecurringYearMonth(id, yearMonth)) {
            throw new IllegalArgumentException("이미 지출로 확정된 고정지출입니다.");
        }
        dailySpendingRepository.save(toSpending(rule, month, member));
    }

    public int confirmAll(YearMonth month, Member member) {
        String yearMonth = month.toString();
        List<DailySpending> spendings = getRules(member).stream()
                .filter(rule -> appliesTo(rule, month))
                .filter(rule -> !dailySpendingRepository
                        .existsByRecurringExpenseIdAndRecurringYearMonth(rule.getId(), yearMonth))
                .map(rule -> toSpending(rule, month, member))
                .toList();
        dailySpendingRepository.saveAll(spendings);
        return spendings.size();
    }

    private DailySpending toSpending(RecurringExpense rule, YearMonth month, Member member) {
        LocalDate spendingDate = month.atDay(Math.min(rule.getPaymentDay(), month.lengthOfMonth()));
        return DailySpending.builder()
                .member(member)
                .spendingDate(spendingDate)
                .categoryCode(rule.getCategoryCode())
                .amount(rule.getAmount())
                .memo(rule.getMemo() == null ? rule.getName() : rule.getName() + " - " + rule.getMemo())
                .recurringExpense(rule)
                .recurringYearMonth(month.toString())
                .build();
    }

    private boolean appliesTo(RecurringExpense rule, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        return rule.isActive()
                && !rule.getStartDate().isAfter(monthEnd)
                && (rule.getEndDate() == null || !rule.getEndDate().isBefore(monthStart));
    }

    private RecurringExpense ownedRule(Long id, Member member) {
        if (id == null) {
            throw new IllegalArgumentException("고정지출을 찾을 수 없습니다.");
        }
        return recurringExpenseRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("고정지출을 찾을 수 없습니다."));
    }

    private void validateCategory(String category) {
        boolean active = masterCodeService.getActiveCodesByGroup("SPENDING_CAT").stream()
                .anyMatch(code -> code.getCodeId().equals(category));
        if (!active) {
            throw new IllegalArgumentException("사용할 수 없는 지출 카테고리입니다.");
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
