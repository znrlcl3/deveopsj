package com.deveopsj.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private AssetPlanRepository assetPlanRepository;

    @Mock
    private AssetSavingsRepository assetSavingsRepository;

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private MasterCodeService masterCodeService;

    @InjectMocks
    private DashboardService dashboardService;

    @Test
    void 이번달_적립액만_월간요약에_집계한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());

        Goal goal = new Goal();
        goal.setTitle("비상금");

        AssetPlan plan = new AssetPlan();
        plan.setId(11L);
        plan.setGoal(goal);
        plan.setAssetType("SAVINGS");
        plan.setMonthlyAmount(500_000L);

        MasterCodeDto savingsCode = MasterCodeDto.builder()
                .groupId("ASSET_TYPE")
                .codeId("SAVINGS")
                .codeName("예적금")
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of(plan));
        when(masterCodeService.getAllActiveCodesGrouped())
                .thenReturn(Map.of("ASSET_TYPE", List.of(savingsCode)));
        when(assetSavingsRepository.getTotalSavingsByPlanIdAndDepositDateBetween(
                plan.getId(), start, end)).thenReturn(200_000L);
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of());

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getTotalInvestment()).isEqualTo(200_000L);
        assertThat(summary.getInvestmentProgress()).isEqualTo(40.0);
        assertThat(summary.getPlanProgressList()).singleElement().satisfies(progress -> {
            assertThat(progress.getActualAmount()).isEqualTo(200_000L);
            assertThat(progress.getProgress()).isEqualTo(40.0);
        });
        verify(assetSavingsRepository).getTotalSavingsByPlanIdAndDepositDateBetween(
                plan.getId(), start, end);
    }

    @Test
    void 지출카테고리를_활성_마스터코드의_한글명으로_집계한다() {
        Long memberId = 7L;
        LocalDate today = LocalDate.now();
        LocalDate start = today.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate end = today.with(TemporalAdjusters.lastDayOfMonth());
        MasterCodeDto foodCode = MasterCodeDto.builder()
                .groupId("SPENDING_CAT")
                .codeId("FOOD")
                .codeName("식비")
                .build();
        DailySpending lunch = DailySpending.builder()
                .categoryCode("FOOD")
                .amount(12_000L)
                .build();
        DailySpending dinner = DailySpending.builder()
                .categoryCode("FOOD")
                .amount(18_000L)
                .build();

        when(assetPlanRepository.findByMemberMemberId(memberId)).thenReturn(List.of());
        when(masterCodeService.getAllActiveCodesGrouped())
                .thenReturn(Map.of("SPENDING_CAT", List.of(foodCode)));
        when(dailySpendingRepository.findByMemberMemberIdAndSpendingDateBetween(
                memberId, start, end)).thenReturn(List.of(lunch, dinner));

        DashboardSummary summary = dashboardService.getMonthlySummary(memberId);

        assertThat(summary.getSpendingByCategory())
                .containsExactly(Map.entry("식비", 30_000L));
    }
}
