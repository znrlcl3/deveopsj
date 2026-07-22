package com.deveopsj.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class DashboardControllerTest {

    @Mock
    private DashboardService dashboardService;

    @InjectMocks
    private DashboardController controller;

    @Test
    void 로그인사용자의_요약만_조회한다() {
        Member member = new Member();
        member.setMemberId(7L);
        DashboardSummary summary = DashboardSummary.builder().build();
        when(dashboardService.getMonthlySummary(7L)).thenReturn(summary);

        ResponseEntity<DashboardSummary> response = controller.getSummary(member);

        assertThat(response.getBody()).isSameAs(summary);
        verify(dashboardService).getMonthlySummary(7L);
    }
}
