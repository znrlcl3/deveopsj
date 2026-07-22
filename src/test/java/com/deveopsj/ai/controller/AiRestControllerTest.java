package com.deveopsj.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AiRestControllerTest {

    @Mock
    private DashboardService dashboardService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private AiRestController controller;

    @Test
    void 로그인사용자의_금융정보만_AI분석에_사용한다() {
        Member member = new Member();
        member.setMemberId(7L);
        DashboardSummary summary = DashboardSummary.builder().build();
        when(dashboardService.getMonthlySummary(7L)).thenReturn(summary);
        when(aiService.getWealthFeedback(summary)).thenReturn("분석 결과");

        Map<String, String> response = controller.getAnalysis(member);

        assertThat(response).containsEntry("feedback", "분석 결과");
        verify(dashboardService).getMonthlySummary(7L);
    }
}
