package com.deveopsj.member.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.member.dto.MemberJoinDto;
import com.deveopsj.member.service.MemberService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/member")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/login")
    public String loginForm() {
        return "member/login";
    }

    @GetMapping("/join")
    public String joinForm() {
        return "member/join";
    }

    @PostMapping("/join-proc")
    public String joinProc(@ModelAttribute MemberJoinDto joinDto, RedirectAttributes redirectAttributes) {
        try {
            memberService.join(joinDto);
            redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요.");
            return "redirect:/member/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/member/join";
        }
    }
}
