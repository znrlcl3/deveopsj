package com.deveopsj.income.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.deveopsj.income.entity.Income;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.income.service.IncomeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.security.CustomUserDetails;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class IncomeViewTest {
    @Autowired MockMvc mvc;
    @MockitoBean IncomeService service;

    @Test
    void showsTypeTotalsAndMonthNavigationWithEscapedMemo() throws Exception {
        var principal = principal();
        when(service.getIncomesByMemberAndMonth(any(), eq(YearMonth.of(2026, 1))))
                .thenReturn(List.of(income(1L, IncomeType.SALARY, 3000000),
                        income(2L, IncomeType.SALARY, 300000), income(3L, IncomeType.SIDE_INCOME, 155000)));
        String html = mvc.perform(get("/income/list").param("month", "2026-01").with(user(principal)))
                .andExpect(status().isOk()).andExpect(model().attribute("totalAmount", 3455000L))
                .andExpect(model().attribute("incomeCount", 3))
                .andExpect(model().attribute("previousMonth", YearMonth.of(2025, 12)))
                .andExpect(model().attribute("nextMonth", YearMonth.of(2026, 2)))
                .andExpect(model().attribute("totalsByType", Map.of(IncomeType.SALARY, 3300000L,
                        IncomeType.SIDE_INCOME, 155000L, IncomeType.DIVIDEND, 0L, IncomeType.INTEREST, 0L, IncomeType.OTHER, 0L)))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("유형별 수입", "3,300,000원", "155,000원", "&lt;script&gt;test&lt;/script&gt;",
                "/income/list?month=2025-12", "/css/income.css").doesNotContain("<script>test</script>");
        verify(service).getIncomesByMemberAndMonth(argThat(m -> m.getMemberId().equals(7L)), eq(YearMonth.of(2026, 1)));
    }

    @Test
    void emptyMonthAndYearBoundsAreHandled() throws Exception {
        when(service.getIncomesByMemberAndMonth(any(), any())).thenReturn(List.of());
        var html = mvc.perform(get("/income/list").param("month", "1900-01").with(user(principal())))
                .andExpect(status().isOk()).andExpect(model().attribute("totalAmount", 0L))
                .andExpect(model().attributeDoesNotExist("previousMonth"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("첫 수입 등록하기", "0원");
        mvc.perform(get("/income/list").param("month", "9999-12").with(user(principal())))
                .andExpect(status().isOk()).andExpect(model().attributeDoesNotExist("nextMonth"));
        mvc.perform(get("/income/list").param("month", "0000-01").with(user(principal())))
                .andExpect(status().isOk()).andExpect(model().attribute("selectedMonth", YearMonth.now()));
    }

    @Test
    void entryOpensListAndFormExplainsAmount() throws Exception {
        mvc.perform(get("/income").with(user(principal())))
                .andExpect(redirectedUrl("/income/list"));
        String html = mvc.perform(get("/income/form").with(user(principal())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("실제 입금된 금액", "단위: 원", "for=\"amount\"", "name=\"_csrf\"");
    }

    @Test
    void anonymousCannotReadIncome() throws Exception {
        mvc.perform(get("/income/list")).andExpect(status().is3xxRedirection());
        verifyNoInteractions(service);
    }

    private Income income(Long id, IncomeType type, long amount) {
        var i = new Income(); i.setId(id); i.setIncomeType(type); i.setAmount(amount);
        i.setIncomeDate(LocalDate.of(2026, 1, 1)); i.setMemo("<script>test</script>"); return i;
    }
    private CustomUserDetails principal() {
        var m = new Member(); m.setMemberId(7L); m.setLoginId("income-test"); m.setName("테스트"); m.setRole("USER");
        return new CustomUserDetails(m);
    }
}
