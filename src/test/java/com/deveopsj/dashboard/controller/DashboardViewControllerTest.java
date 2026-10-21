package com.deveopsj.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.deveopsj.ai.config.GeminiProperties;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class DashboardViewControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private GeminiProperties geminiProperties;

    @InjectMocks
    private DashboardViewController controller;

    @Test
    void 실제_설정된_AI모델명을_대시보드에_전달한다() {
        Member member = new Member();
        member.setMemberId(7L);
        DashboardSummary summary = DashboardSummary.builder().build();
        ExtendedModelMap model = new ExtendedModelMap();
        when(dashboardService.getMonthlySummary(7L, java.time.YearMonth.now())).thenReturn(summary);
        when(geminiProperties.getModel()).thenReturn("gemini-3.1-flash-lite");

        String viewName = controller.dashboard(null, model, member);

        assertThat(viewName).isEqualTo("dashboard");
        assertThat(model.get("summary")).isSameAs(summary);
        assertThat(model.get("aiModel")).isEqualTo("gemini-3.1-flash-lite");
        assertThat(model.get("analysisMonth")).isEqualTo(java.time.YearMonth.now());
        assertThat(model).doesNotContainKey("analysisTypes");
    }

    @Test
    void selectedMonthAndInvalidMonthUseAuthenticatedMember() {
        Member member = new Member();
        member.setMemberId(7L);
        var month = java.time.YearMonth.of(2024, 2);
        when(dashboardService.getMonthlySummary(7L, month)).thenReturn(DashboardSummary.builder().build());
        var model = new ExtendedModelMap();
        controller.dashboard("2024-02", model, member);
        assertThat(model.get("selectedMonth")).isEqualTo(month);
        assertThat(model.get("analysisMonth")).isEqualTo(month);
        org.mockito.Mockito.verify(dashboardService).getMonthlySummary(7L, month);
        when(dashboardService.getMonthlySummary(7L, java.time.YearMonth.now()))
                .thenReturn(DashboardSummary.builder().build());
        controller.dashboard("bad-month", model, member);
        assertThat(model.get("selectedMonth")).isEqualTo(java.time.YearMonth.now());
        assertThat(model.get("errorMessage")).isNotNull();
    }
}
