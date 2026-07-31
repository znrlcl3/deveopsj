package com.deveopsj.member.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ConcurrentModel;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.deveopsj.member.dto.MemberJoinDto;
import com.deveopsj.member.service.MemberService;

class MemberControllerTest {

    @Test
    void 가입이_비활성화되면_가입화면을_로그인으로_보낸다() {
        MemberController controller = new MemberController(mock(MemberService.class), false);
        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();

        String view = controller.joinForm(redirectAttributes);

        assertThat(view).isEqualTo("redirect:/member/login");
        assertThat(redirectAttributes.getFlashAttributes().get("errorMessage"))
                .isEqualTo("현재 회원가입을 받지 않고 있습니다.");
    }

    @Test
    void 가입이_비활성화되면_가입요청을_저장하지_않는다() {
        MemberService memberService = mock(MemberService.class);
        MemberController controller = new MemberController(memberService, false);
        MemberJoinDto joinDto = new MemberJoinDto();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(joinDto, "joinDto");

        String view = controller.joinProc(
                joinDto, bindingResult, new RedirectAttributesModelMap());

        assertThat(view).isEqualTo("redirect:/member/login");
        verifyNoInteractions(memberService);
    }

    @Test
    void 로그인화면에_회원가입_가능여부를_전달한다() {
        MemberController controller = new MemberController(mock(MemberService.class), true);
        ConcurrentModel model = new ConcurrentModel();

        String view = controller.loginForm(model);

        assertThat(view).isEqualTo("member/login");
        assertThat(model.getAttribute("registrationEnabled")).isEqualTo(true);
    }
}
