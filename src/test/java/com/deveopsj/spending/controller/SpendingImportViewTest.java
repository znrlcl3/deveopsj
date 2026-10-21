package com.deveopsj.spending.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.security.CustomUserDetails;
import com.deveopsj.spending.dto.*;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.service.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpendingImportViewTest {
    @Autowired MockMvc mvc;
    @MockitoBean SpendingService spendingService;
    @MockitoBean RecurringExpenseService recurringExpenseService;
    @MockitoBean SpendingExcelImportService excelService;
    @MockitoBean CardSpendingImportService cardService;
    @MockitoBean MasterCodeService masterCodes;

    private final LocalDateTime created = LocalDateTime.of(2026, 8, 30, 10, 20);

    @BeforeEach
    void codes() {
        when(masterCodes.getAllActiveCodesGrouped()).thenReturn(Map.of("SPENDING_CAT", List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build())));
        when(masterCodes.getCodeName("SPENDING_CAT", "FOOD")).thenReturn("식비");
    }

    @Test
    void listRendersCreatedTimestampAndHandlesLegacyNullDate() throws Exception {
        var first = DailySpending.builder().id(1L).spendingDate(LocalDate.of(2026, 8, 29))
                .categoryCode("FOOD").amount(10000L).memo("점심").build();
        first.setCreateDate(created);
        var legacy = DailySpending.builder().id(2L).spendingDate(LocalDate.of(2026, 8, 29))
                .categoryCode("FOOD").amount(30000L).memo("기존 내역")
                .recurringExpense(new com.deveopsj.spending.entity.RecurringExpense()).build();
        when(spendingService.getSpendings(any(), any(), any())).thenReturn(List.of(first, legacy));
        when(recurringExpenseService.getOccurrences(any(), any())).thenReturn(List.of());
        mvc.perform(get("/spending/list").param("month", "2026-08").with(user(principal())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("등록 일시")))
                .andExpect(model().attribute("consumptionAmount", 10000L))
                .andExpect(model().attribute("fixedExpenseAmount", 30000L))
                .andExpect(content().string(containsString(">소비지출</span>")))
                .andExpect(content().string(containsString(">고정지출</span>")))
                .andExpect(content().string(containsString("2026-08-30 10:20")));
    }

    @Test
    void excelPreviewShowsExistingRegistrationAndEscapesMemo() throws Exception {
        var row = new SpendingExcelImportRow(2, LocalDate.of(2026, 8, 29), "FOOD", 10000,
                "<script>bad</script>", "key", List.of(new SpendingDuplicateMatch(99L, "FOOD", created)), false, false);
        var preview = new SpendingExcelImportPreview("token", 7L, "sample.xlsx", "2026-08", List.of(row));
        when(excelService.preview(any(), any(), eq("2026-08"))).thenReturn(preview);
        String html = mvc.perform(multipart("/spending/import")
                .file(new MockMultipartFile("file", "sample.xlsx", null, new byte[] {1}))
                .param("month", "2026-08").with(user(principal())).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("spending/excel-import-preview"))
                .andExpect(content().string(containsString("기존 #99")))
                .andExpect(content().string(containsString("2026-08-30 10:20")))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("&lt;script&gt;bad&lt;/script&gt;")
                .doesNotContain("<script>bad</script>");
        var input = java.util.regex.Pattern.compile("<input[^>]*type=\"checkbox\"[^>]*>").matcher(html);
        org.assertj.core.api.Assertions.assertThat(input.find()).isTrue();
        org.assertj.core.api.Assertions.assertThat(input.group()).doesNotContain("checked");
        verify(excelService, never()).importFile(any(), any());
    }

    @Test
    void cardPreviewShowsDuplicateCandidateAndLeavesItUnchecked() throws Exception {
        var row = new CardSpendingImportRow(4, LocalDate.of(2026, 8, 1), "가맹점", 10000, "key", null,
                "할부", List.of(new SpendingDuplicateMatch(99L, "FOOD", created)), false, false);
        var preview = new CardImportPreview("token", CardCompany.SAMSUNG, "sample.xlsx", List.of(row), null, 7L);
        when(cardService.preview(eq(CardCompany.SAMSUNG), any(), any(), any(), any())).thenReturn(preview);
        String html = mvc.perform(multipart("/spending/card-import/preview")
                .file(new MockMultipartFile("file", "sample.xlsx", null, new byte[] {1}))
                .param("cardCompany", "SAMSUNG").param("billingMonth", "2026-08")
                .with(user(principal())).with(csrf()))
                .andExpect(status().isOk()).andExpect(view().name("spending/import-preview"))
                .andExpect(content().string(containsString("할부 4행")))
                .andExpect(content().string(containsString("기존 #99")))
                .andReturn().getResponse().getContentAsString();
        var input = java.util.regex.Pattern.compile("<input[^>]*type=\"checkbox\"[^>]*>").matcher(html);
        org.assertj.core.api.Assertions.assertThat(input.find()).isTrue();
        org.assertj.core.api.Assertions.assertThat(input.group()).doesNotContain("checked");
    }

    @Test
    void confirmRequiresAuthenticationAndCsrfBeforeInvokingService() throws Exception {
        mvc.perform(post("/spending/import/confirm").param("token", "token").param("selectedRows", "0"));
        mvc.perform(post("/spending/import/confirm").with(user(principal()))
                .param("token", "token").param("selectedRows", "0"));
        verify(excelService, never()).confirm(any(), any(), any());
    }

    private CustomUserDetails principal() {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("test");
        member.setName("테스트");
        member.setRole("USER");
        return new CustomUserDetails(member);
    }

    @Test
    void installmentPreviewShowsTotalEveryRoundAndExplicitReviewWithoutDefaultSelection() throws Exception {
        var plan = new CardInstallmentPlan(100000, 3, LocalDate.of(2024, 1, 31));
        var row = new CardSpendingImportRow(2, LocalDate.of(2024, 1, 5), "<b>할부</b>", 100000,
                "key", null, "국내", List.of(), false, false, plan);
        var preview = new CardImportPreview("token", CardCompany.SAMSUNG, "synthetic.xlsx", List.of(row), "예상 원금", 7L);
        when(cardService.preview(eq(CardCompany.SAMSUNG), any(), any(), any(), any())).thenReturn(preview);
        var html = mvc.perform(multipart("/spending/card-import/preview")
                .file(new MockMultipartFile("file", "synthetic.xlsx", null, new byte[] {1}))
                .param("cardCompany", "SAMSUNG").with(user(principal())).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(html).contains("예상 원금", "수수료", "100,000원", "33,334원",
                "2024-02-29", "reviewed_0", "firstPayment_0", "&lt;b&gt;할부&lt;/b&gt;");
        var selected = java.util.regex.Pattern.compile("<input[^>]*name=\"selectedRows\"[^>]*>").matcher(html);
        org.assertj.core.api.Assertions.assertThat(selected.find()).isTrue();
        org.assertj.core.api.Assertions.assertThat(selected.group()).doesNotContain("checked", "disabled");
    }
}

