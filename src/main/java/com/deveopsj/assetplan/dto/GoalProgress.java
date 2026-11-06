package com.deveopsj.assetplan.dto;

import com.deveopsj.assetplan.entity.Goal;

public record GoalProgress(Goal goal, long savedAmount, long remainingAmount, double progress,
        int planCount, int depositCount, Long daysRemaining, Long monthlyNeeded, String displayStatus) {}
