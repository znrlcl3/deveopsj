package com.deveopsj.member.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.member.service.MemberService;
import com.deveopsj.spending.service.SpendingService;

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

    @Test
    void 로그인_화면은_리디렉션없이_표시한다() throws Exception {
        mockMvc.perform(get("/member/login"))
                .andExpect(status().isOk());
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
}
