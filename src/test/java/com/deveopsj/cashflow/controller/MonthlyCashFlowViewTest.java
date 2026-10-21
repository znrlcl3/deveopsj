package com.deveopsj.cashflow.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.deveopsj.cashflow.dto.MonthlyCashFlowSummary;
import com.deveopsj.cashflow.service.MonthlyCashFlowService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.security.CustomUserDetails;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonthlyCashFlowViewTest {
    @Autowired MockMvc mvc;
    @MockitoBean MonthlyCashFlowService service;

    @Test
    void rendersBreakdownPendingDeficitAndSelectedMonthLinks() throws Exception {
        var month = YearMonth.of(2026, 7);
        when(service.getMonthlyCashFlow(any(), eq(month))).thenReturn(new MonthlyCashFlowSummary(
                month, 3000000, 1000000, 1500000, 500000, 1100000, 0, 0, 0, 1100000,
                1100000, List.of(), 700000, 300000, 600000, 50000));
        String html = mvc.perform(get("/cashflow").param("month", "2026-07").with(user(principal())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("소비지출", "고정지출", "700,000원", "400,000원",
                "-150,000원", "/spending/list?month=2026-07", "/savings/list?month=2026-07",
                "선택 월말까지 누적", "아직 확정하지 않은 예정액")
                .doesNotContain("미배분 현금", "<details open");
        String preview = System.getProperty("cashflow.preview.path");
        if (preview != null) java.nio.file.Files.writeString(java.nio.file.Path.of(preview), html);
    }

    @Test
    void emptyMonthRendersZeroAndNoPendingMessage() throws Exception {
        when(service.getMonthlyCashFlow(any(), any())).thenReturn(new MonthlyCashFlowSummary(
                YearMonth.now(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, List.of(), 0, 0, 0, 0));
        String html = mvc.perform(get("/cashflow").with(user(principal())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("이 달에 남은 미확정 예정액이 없습니다.", "0원");
    }

    @Test
    void anonymousCannotReadCashFlow() throws Exception {
        mvc.perform(get("/cashflow")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }

    private CustomUserDetails principal() {
        var member = new Member();
        member.setMemberId(7L); member.setLoginId("test"); member.setName("테스트"); member.setRole("USER");
        return new CustomUserDetails(member);
    }
}
