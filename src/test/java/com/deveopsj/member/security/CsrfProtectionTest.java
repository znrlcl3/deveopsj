package com.deveopsj.member.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.not;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.deveopsj.member.service.MemberService;

@SpringBootTest
@AutoConfigureMockMvc
class CsrfProtectionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MemberService memberService;

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
}
