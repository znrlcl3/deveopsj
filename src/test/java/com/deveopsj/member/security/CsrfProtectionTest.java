package com.deveopsj.member.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.ai.service.SpendingAnalysisService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.service.MemberService;
import com.deveopsj.spending.service.SpendingService;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.assetplan.service.AssetValuationService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.market.service.InvestmentAssetSyncService;

@SpringBootTest
@AutoConfigureMockMvc
class CsrfProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

    @MockitoBean
    private DataInputService dataInputService;

    @MockitoBean
    private SpendingService spendingService;

    @MockitoBean
    private SpendingAnalysisService spendingAnalysisService;

    @MockitoBean
    private DashboardService dashboardService;

    @MockitoBean
    private AssetPlanService assetPlanService;

    @MockitoBean
    private AssetSavingsService assetSavingsService;

    @MockitoBean
    private AssetTradeService assetTradeService;

    @MockitoBean
    private AssetValuationService assetValuationService;

    @MockitoBean
    private GoalService goalService;

    @MockitoBean
    private InvestmentAssetSyncService investmentAssetSyncService;

    @Test
    void 로그인_화면은_리디렉션없이_표시한다() throws Exception {
        mockMvc.perform(get("/member/login"))
                .andExpect(status().isOk());
    }

    @Test
    void 로그인사용자가_루트주소에_접속하면_대시보드로_이동한다() throws Exception {
        mockMvc.perform(get("/").with(user("user")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard/view"));
    }

    @Test
    void 비로그인사용자가_루트주소에_접속하면_로그인화면으로_이동한다() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/member/login"));
    }

    @Test
    void 오류_페이지는_로그인으로_리디렉션하지_않는다() throws Exception {
        mockMvc.perform(get("/error"))
                .andExpect(status().is(not(302)));
    }

    @Test
    void CSRF_토큰이_없는_POST_요청은_거부한다() throws Exception {
        mockMvc.perform(post("/member/join-proc")
                        .param("loginId", "user")
                        .param("password", "password")
                        .param("name", "사용자"))
                .andExpect(status().isForbidden());
    }

    @Test
    void CSRF_토큰이_있는_POST_요청은_처리한다() throws Exception {
        mockMvc.perform(post("/member/join-proc")
                        .with(csrf())
                        .param("loginId", "user")
                        .param("password", "password")
                        .param("name", "사용자"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void 잘못된_지출_JSON은_서비스호출전에_거부한다() throws Exception {
        mockMvc.perform(post("/spending/api/save")
                        .with(user("user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2999-01-01",
                                  "amount": 0,
                                  "memo": " "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dataInputService);
    }

    @Test
    void 로그인사용자는_선택한_월의_지출목록을_조회한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");

        mockMvc.perform(get("/spending/list")
                        .param("month", "2026-07")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(view().name("spending/list"))
                .andExpect(model().attribute("selectedMonth", java.time.YearMonth.of(2026, 7)));

        verify(spendingService).getSpendings(
                member, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31));
    }

    @Test
    void 미래월의_AI지출분석은_서비스호출전에_거부한다() throws Exception {
        String futureMonth = java.time.YearMonth.now().plusMonths(1).toString();

        mockMvc.perform(post("/api/ai/spending-analysis")
                        .with(user("user"))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "month": "%s",
                                  "analysisType": "CATEGORY_REVIEW"
                                }
                                """.formatted(futureMonth)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(spendingAnalysisService);
    }

    @Test
    void 대시보드에_네가지_AI지출분석카드를_표시한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        when(dashboardService.getMonthlySummary(7L)).thenReturn(DashboardSummary.builder()
                .totalInvestment(0L)
                .totalSpending(0L)
                .spendingByCategory(java.util.Map.of())
                .investmentProgress(0.0)
                .planProgressList(java.util.List.of())
                .build());

        mockMvc.perform(get("/dashboard/view")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("절약 가능 지출 찾기")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("카테고리별 소비 평가")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("지난달과 비교")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("다음 달 절약 계획")));
    }

    @Test
    void 자산플랜_내역화면은_수정삭제_기능을_표시한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        when(assetPlanService.getPlansByMember(member)).thenReturn(java.util.List.of());
        when(goalService.getGoalsByMember(member)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/assetplan/list")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("자산 플랜 내역")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("새 플랜 등록")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("등록된 자산 플랜이 없습니다.")));
    }

    @Test
    void 납입등록화면은_단순납입항목만_렌더링한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        Goal goal = new Goal();
        goal.setTitle("1억 모으기");
        AssetPlan plan = new AssetPlan();
        plan.setId(3L);
        plan.setPlanName("ISA 계좌");
        plan.setGoal(goal);
        plan.setAssetType("SECURITIES");
        plan.setMonthlyAmount(1_100_000L);
        when(assetSavingsService.getDepositPlansByMember(member)).thenReturn(java.util.List.of(plan));

        mockMvc.perform(get("/savings/form")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("납입 등록")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "ISA 계좌 · 목표: 1억 모으기 · 월 1,100,000원")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("inputmode=\"numeric\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("amount-input")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/savings/list")))
                .andExpect(content().string(not(org.hamcrest.Matchers.containsString("/valuations/save"))));
    }

    @Test
    void 납입내역화면은_월조회와_수정삭제기능을_표시한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        java.time.YearMonth month = java.time.YearMonth.now();
        when(assetSavingsService.getDepositPlansByMember(member)).thenReturn(java.util.List.of());
        when(assetSavingsService.getSavingsByMemberAndMonth(member, month)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/savings/list")
                        .with(user(new CustomUserDetails(member)))
                        .param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("납입 내역")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("월 납입 합계")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("새 납입 등록")));
    }

    @Test
    void 매매등록화면을_렌더링한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        when(assetPlanService.getPlansByMember(member)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/trades/form")
                        .with(user(new CustomUserDetails(member))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주식·ETF 매매 등록")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/trades/list")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/trades/save")));
    }

    @Test
    void 매매내역화면은_월조회와_수정삭제기능을_표시한다() throws Exception {
        Member member = new Member();
        member.setMemberId(7L);
        member.setLoginId("user");
        member.setPassword("password");
        member.setRole("USER");
        java.time.YearMonth month = java.time.YearMonth.now();
        when(assetPlanService.getPlansByMember(member)).thenReturn(java.util.List.of());
        when(assetTradeService.getTradesByMemberAndMonth(member, month)).thenReturn(java.util.List.of());

        mockMvc.perform(get("/trades/list")
                        .with(user(new CustomUserDetails(member)))
                        .param("month", month.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("주식·ETF 매매 내역")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("조회 월")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("새 매매 등록")));
    }

    @Test
    void CSRF토큰이_없는_자산플랜수정은_거부한다() throws Exception {
        mockMvc.perform(post("/assetplan/update")
                        .with(user("user"))
                        .param("id", "10")
                        .param("goalId", "3")
                        .param("assetType", "SAVINGS")
                        .param("monthlyAmount", "100000"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assetPlanService);
    }

    @Test
    void CSRF토큰이_없는_자산평가등록은_거부한다() throws Exception {
        mockMvc.perform(post("/valuations/save")
                        .with(user("user"))
                        .param("assetPlanId", "10")
                        .param("valuationAmount", "120000")
                        .param("valuationDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assetValuationService);
    }

    @Test
    void CSRF토큰이_없는_매매등록은_거부한다() throws Exception {
        mockMvc.perform(post("/trades/save")
                        .with(user("user"))
                        .param("assetPlanId", "10")
                        .param("symbol", "123456")
                        .param("assetName", "ACE 다우100")
                        .param("market", "KOSPI")
                        .param("assetClass", "ETF")
                        .param("tradeType", "BUY")
                        .param("tradeDate", LocalDate.now().toString())
                        .param("quantity", "10")
                        .param("tradeAmount", "235000")
                        .param("currency", "KRW")
                        .param("exchangeRate", "1")
                        .param("feeKrw", "0")
                        .param("taxKrw", "0"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assetTradeService);
    }

    @Test
    void CSRF토큰이_없는_종목마스터동기화는_거부한다() throws Exception {
        mockMvc.perform(post("/krx/sync")
                        .with(user("user"))
                        .param("date", LocalDate.now().toString())
                        .param("market", "KOSPI"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(investmentAssetSyncService);
    }

    @Test
    void CSRF토큰이_없는_매매수정은_거부한다() throws Exception {
        mockMvc.perform(post("/trades/update")
                        .with(user("user"))
                        .param("id", "9")
                        .param("month", java.time.YearMonth.now().toString())
                        .param("assetPlanId", "10")
                        .param("symbol", "123456")
                        .param("assetName", "ACE 다우100")
                        .param("market", "KOSPI")
                        .param("assetClass", "ETF")
                        .param("tradeType", "BUY")
                        .param("tradeDate", LocalDate.now().toString())
                        .param("quantity", "10")
                        .param("tradeAmount", "235000")
                        .param("currency", "KRW")
                        .param("exchangeRate", "1")
                        .param("feeKrw", "0")
                        .param("taxKrw", "0"))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assetTradeService);
    }

    @Test
    void CSRF토큰이_없는_납입수정은_거부한다() throws Exception {
        mockMvc.perform(post("/savings/update")
                        .with(user("user"))
                        .param("id", "9")
                        .param("assetPlanId", "3")
                        .param("amount", "150000")
                        .param("depositDate", LocalDate.now().toString()))
                .andExpect(status().isForbidden());

        verifyNoInteractions(assetSavingsService);
    }
}
