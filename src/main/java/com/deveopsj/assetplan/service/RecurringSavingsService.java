package com.deveopsj.assetplan.service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.assetplan.dto.RecurringSavingsOccurrence;
import com.deveopsj.assetplan.dto.RecurringSavingsRequest;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.entity.AssetSavings.DepositType;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.entity.RecurringSavings;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.assetplan.repository.GoalRepository;
import com.deveopsj.assetplan.repository.RecurringSavingsRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class RecurringSavingsService {

    private final RecurringSavingsRepository recurringSavingsRepository;
    private final AssetSavingsRepository assetSavingsRepository;
    private final AssetPlanRepository assetPlanRepository;
    private final GoalRepository goalRepository;

    @Transactional(readOnly = true)
    public List<RecurringSavings> getRules(Member member) {
        return recurringSavingsRepository
                .findByMemberMemberIdOrderByActiveDescPaymentDayAscNameAsc(member.getMemberId());
    }

    public void save(RecurringSavingsRequest request, Member member) {
        DepositTarget target = resolveTarget(request, member);
        recurringSavingsRepository.save(RecurringSavings.builder()
                .member(member)
                .name(request.getName().trim())
                .assetPlan(target.assetPlan())
                .goal(target.goal())
                .depositType(request.getDepositType())
                .amount(request.getAmount())
                .paymentDay(request.getPaymentDay())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .active(true)
                .memo(trimToNull(request.getMemo()))
                .build());
    }

    public void update(RecurringSavingsRequest request, Member member) {
        RecurringSavings rule = ownedRule(request.getId(), member);
        DepositTarget target = resolveTarget(request, member);
        rule.setName(request.getName().trim());
        rule.setAssetPlan(target.assetPlan());
        rule.setGoal(target.goal());
        rule.setDepositType(request.getDepositType());
        rule.setAmount(request.getAmount());
        rule.setPaymentDay(request.getPaymentDay());
        rule.setStartDate(request.getStartDate());
        rule.setEndDate(request.getEndDate());
        rule.setMemo(trimToNull(request.getMemo()));
    }

    public void toggle(Long id, Member member) {
        RecurringSavings rule = ownedRule(id, member);
        rule.setActive(!rule.isActive());
    }

    @Transactional(readOnly = true)
    public List<RecurringSavingsOccurrence> getOccurrences(Member member, YearMonth month) {
        String yearMonth = month.toString();
        return getRules(member).stream()
                .filter(rule -> appliesTo(rule, month))
                .map(rule -> new RecurringSavingsOccurrence(
                        rule,
                        scheduledDate(rule, month),
                        assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(
                                rule.getId(), yearMonth)))
                .toList();
    }

    public void confirm(Long id, YearMonth month, Member member) {
        RecurringSavings rule = ownedRule(id, member);
        if (!appliesTo(rule, month)) {
            throw new IllegalArgumentException("해당 월에 적용되는 정기 납입이 아닙니다.");
        }
        String yearMonth = month.toString();
        if (assetSavingsRepository.existsByRecurringSavingsIdAndRecurringYearMonth(id, yearMonth)) {
            throw new IllegalArgumentException("이미 확정된 정기 납입입니다.");
        }
        assetSavingsRepository.save(toSavings(rule, month));
    }

    public int confirmAll(YearMonth month, Member member) {
        String yearMonth = month.toString();
        List<AssetSavings> savings = getRules(member).stream()
                .filter(rule -> appliesTo(rule, month))
                .filter(rule -> !assetSavingsRepository
                        .existsByRecurringSavingsIdAndRecurringYearMonth(rule.getId(), yearMonth))
                .map(rule -> toSavings(rule, month))
                .toList();
        assetSavingsRepository.saveAll(savings);
        return savings.size();
    }

    private AssetSavings toSavings(RecurringSavings rule, YearMonth month) {
        return AssetSavings.builder()
                .assetPlan(rule.getAssetPlan())
                .goal(rule.getGoal())
                .depositType(rule.getDepositType())
                .amount(rule.getAmount())
                .depositDate(scheduledDate(rule, month))
                .memo(rule.getMemo() == null ? rule.getName() : rule.getName() + " - " + rule.getMemo())
                .recurringSavings(rule)
                .recurringYearMonth(month.toString())
                .build();
    }

    private LocalDate scheduledDate(RecurringSavings rule, YearMonth month) {
        return month.atDay(Math.min(rule.getPaymentDay(), month.lengthOfMonth()));
    }

    private boolean appliesTo(RecurringSavings rule, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        LocalDate monthEnd = month.atEndOfMonth();
        return rule.isActive()
                && !rule.getStartDate().isAfter(monthEnd)
                && (rule.getEndDate() == null || !rule.getEndDate().isBefore(monthStart));
    }

    private RecurringSavings ownedRule(Long id, Member member) {
        if (id == null) {
            throw new IllegalArgumentException("정기 납입을 찾을 수 없습니다.");
        }
        return recurringSavingsRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("정기 납입을 찾을 수 없습니다."));
    }

    private DepositTarget resolveTarget(RecurringSavingsRequest request, Member member) {
        if (request.getDepositType() == DepositType.PLAN) {
            AssetPlan plan = assetPlanRepository
                    .findByIdAndMemberMemberId(request.getAssetPlanId(), member.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 자산 플랜을 사용할 수 없습니다."));
            return new DepositTarget(plan, plan.getGoal());
        }
        if (request.getDepositType() == DepositType.EXTRA) {
            Goal goal = goalRepository
                    .findByIdAndMemberMemberId(request.getGoalId(), member.getMemberId())
                    .orElseThrow(() -> new IllegalArgumentException("선택한 목표를 사용할 수 없습니다."));
            return new DepositTarget(null, goal);
        }
        throw new IllegalArgumentException("납입 유형을 확인해 주세요.");
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record DepositTarget(AssetPlan assetPlan, Goal goal) {
    }
}
