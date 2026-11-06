package com.deveopsj.assetplan.service;

import com.deveopsj.assetplan.dto.GoalProgress;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service @RequiredArgsConstructor
@Transactional(readOnly = true)
public class GoalProgressService {
    private final AssetPlanRepository plans;
    private final AssetSavingsRepository savings;

    public List<GoalProgress> summarize(Member member, List<Goal> goals) {
        if (goals.isEmpty()) return List.of();
        var today = LocalDate.now();
        var amounts = new HashMap<Long, Long>();
        var counts = new HashMap<Long, Integer>();
        var planCounts = new HashMap<Long, Integer>();
        for (var plan : plans.findByMemberMemberId(member.getMemberId())) {
            if (owned(plan.getGoal(), member)) planCounts.merge(plan.getGoal().getId(), 1, Integer::sum);
        }
        for (var saving : savings.findAllByMemberIdAndDepositDateLessThanEqual(member.getMemberId(), today)) {
            Goal goal = saving.getAssetPlan() != null ? saving.getAssetPlan().getGoal() : saving.getGoal();
            if (!owned(goal, member) || saving.getAmount() == null || saving.getAmount() <= 0
                    || saving.getDepositDate() == null || saving.getDepositDate().isAfter(today)) continue;
            if (saving.getAssetPlan() != null && (saving.getAssetPlan().getMember() == null
                    || !Objects.equals(saving.getAssetPlan().getMember().getMemberId(), member.getMemberId()))) continue;
            if (saving.getGoal() != null && (!owned(saving.getGoal(), member)
                    || !Objects.equals(saving.getGoal().getId(), goal.getId()))) continue;
            amounts.merge(goal.getId(), saving.getAmount(), Math::addExact);
            counts.merge(goal.getId(), 1, Integer::sum);
        }
        return goals.stream().filter(goal -> owned(goal, member))
                .sorted(Comparator.comparing(Goal::getId).reversed())
                .map(goal -> {
                    long saved = amounts.getOrDefault(goal.getId(), 0L);
                    long remaining = Math.max(0L, Math.subtractExact(goal.getTargetAmount(), saved));
                    Long days = goal.getType() == Goal.GoalType.TERM && goal.getEndDate() != null
                            ? ChronoUnit.DAYS.between(today, goal.getEndDate()) : null;
                    Long monthly = null;
                    if (days != null && days >= 0 && remaining > 0 && "IN_PROGRESS".equals(goal.getStatus())) {
                        long months = ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(goal.getEndDate())) + 1;
                        monthly = remaining / months + (remaining % months == 0 ? 0 : 1);
                    }
                    String status = "COMPLETED".equals(goal.getStatus()) ? "완료"
                            : "FAILED".equals(goal.getStatus()) ? "미달성 종료"
                            : remaining == 0 ? "목표 금액 도달" : days != null && days < 0 ? "기한 지남" : "진행 중";
                    return new GoalProgress(goal, saved, remaining,
                            goal.getTargetAmount() > 0 ? Math.min(100.0, saved * 100.0 / goal.getTargetAmount()) : 0,
                            planCounts.getOrDefault(goal.getId(), 0), counts.getOrDefault(goal.getId(), 0), days, monthly, status);
                }).toList();
    }

    private boolean owned(Goal goal, Member member) {
        return goal != null && goal.getMember() != null
                && Objects.equals(goal.getMember().getMemberId(), member.getMemberId());
    }
}
