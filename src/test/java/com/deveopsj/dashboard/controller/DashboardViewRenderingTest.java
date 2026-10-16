package com.deveopsj.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardViewRenderingTest {
    @Autowired MockMvc mvc;
    @MockitoBean DashboardService service;

    @Test
    void rendersSelectedMonthRegistrationAndEscapedMemo() throws Exception {
        var summary = summary(List.of(new DashboardSummary.RecentSpending(LocalDate.of(2024, 2, 29),
                "식비", 12000L, "<script>bad</script>", LocalDateTime.of(2024, 3, 1, 10, 20))));
        when(service.getMonthlySummary(7L, YearMonth.of(2024, 2))).thenReturn(summary);
        String html = mvc.perform(get("/dashboard/view").param("month", "2024-02").with(user(principal())))
                .andExpect(status().isOk()).andExpect(view().name("dashboard"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("기록상 잔액", "2024-02", "2024-03-01 10:20", "&lt;script&gt;bad&lt;/script&gt;")
                .doesNotContain("<script>bad</script>", "spendingChart");
        assertThat(html.indexOf("최근 지출")).isLessThan(html.indexOf("AI로 소비 패턴"));
        verify(service).getMonthlySummary(7L, YearMonth.of(2024, 2));
        String previewPath = System.getProperty("dashboard.preview.path");
        if (previewPath != null) {
            java.nio.file.Files.writeString(java.nio.file.Path.of(previewPath), html);
        }
    }

    @Test
    void emptyMonthRendersGuidanceWithoutChart() throws Exception {
        when(service.getMonthlySummary(eq(7L), any())).thenReturn(summary(List.of()));
        var html = mvc.perform(get("/dashboard/view").with(user(principal())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("첫 지출 기록하기", "아직 이 달의 지출 기록이 없어요");
    }

    @Test
    void anonymousRequestDoesNotReadFinancialData() throws Exception {
        mvc.perform(get("/dashboard/view")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }

    private DashboardSummary summary(List<DashboardSummary.RecentSpending> rows) {
        return DashboardSummary.builder().totalIncome(30000L).totalSpending(rows.isEmpty() ? 0L : 12000L)
                .totalInvestment(0L).totalInvestmentTarget(0L).extraInvestment(0L).availableCash(18000L)
                .savingsRate(0.0).investmentProgress(0.0).planProgressList(List.of())
                .spendingCount(rows.size()).recentSpendings(rows)
                .spendingByCategory(rows.isEmpty() ? Map.of() : Map.of("식비", 12000L)).build();
    }

    private CustomUserDetails principal() {
        Member member = new Member();
        member.setMemberId(7L); member.setLoginId("test"); member.setName("테스트"); member.setRole("USER");
        return new CustomUserDetails(member);
    }
}
