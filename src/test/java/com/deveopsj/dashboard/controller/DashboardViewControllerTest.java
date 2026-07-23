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
        when(dashboardService.getMonthlySummary(7L)).thenReturn(summary);
        when(geminiProperties.getModel()).thenReturn("gemini-3.1-flash-lite");

        String viewName = controller.dashboard(model, member);

        assertThat(viewName).isEqualTo("dashboard");
        assertThat(model.get("summary")).isSameAs(summary);
        assertThat(model.get("aiModel")).isEqualTo("gemini-3.1-flash-lite");
    }
}
