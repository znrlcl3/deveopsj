package com.deveopsj.member.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;

import com.deveopsj.member.dto.MemberJoinDto;
import com.deveopsj.member.dto.PasswordChangeDto;
import com.deveopsj.member.dto.AccountDeactivationDto;
import com.deveopsj.member.security.MemberPrincipal;
import com.deveopsj.member.service.MemberService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
@Controller
@RequestMapping("/member")
public class MemberController {

    private final MemberService memberService;
    private final boolean registrationEnabled;
    private final boolean oidcEnabled;

    public MemberController(MemberService memberService,
            @Value("${app.registration.enabled:true}") boolean registrationEnabled,
            @Value("${app.security.oidc.enabled:false}") boolean oidcEnabled) {
        this.memberService = memberService;
        this.registrationEnabled = registrationEnabled;
        this.oidcEnabled = oidcEnabled;
    }

    @GetMapping("/login")
    public String loginForm(Model model) {
        model.addAttribute("registrationEnabled", registrationEnabled);
        model.addAttribute("oidcEnabled", oidcEnabled);
        return "member/login";
    }

    @GetMapping("/join")
    public String joinForm(RedirectAttributes redirectAttributes) {
        if (!registrationEnabled) {
            redirectAttributes.addFlashAttribute("errorMessage", "현재 회원가입을 받지 않고 있습니다.");
            return "redirect:/member/login";
        }
        return "member/join";
    }

    @PostMapping("/join-proc")
    public String joinProc(@Valid @ModelAttribute MemberJoinDto joinDto,
            BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (!registrationEnabled) {
            redirectAttributes.addFlashAttribute("errorMessage", "현재 회원가입을 받지 않고 있습니다.");
            return "redirect:/member/login";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/member/join";
        }
        try {
            memberService.join(joinDto);
            redirectAttributes.addFlashAttribute("successMessage", "회원가입이 완료되었습니다. 로그인해 주세요.");
            return "redirect:/member/login";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/member/join";
        }
    }

    @GetMapping("/password")
    public String passwordForm(@AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (principal.isExternal()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "SSO 계정의 비밀번호는 Keycloak에서 변경해 주세요.");
            return "redirect:/dashboard/view";
        }
        return "member/password";
    }

    @PostMapping("/password")
    public String changePassword(
            @Valid @ModelAttribute PasswordChangeDto changeDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        if (principal.isExternal()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "SSO 계정의 비밀번호는 Keycloak에서 변경해 주세요.");
            return "redirect:/dashboard/view";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/member/password";
        }
        try {
            memberService.changePassword(principal.getLoginId(), changeDto);
            clearLoginSession(request);
            redirectAttributes.addFlashAttribute(
                    "successMessage", "비밀번호가 변경되었습니다. 다시 로그인해 주세요.");
            return "redirect:/member/login";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/member/password";
        }
    }

    @GetMapping("/deactivate")
    public String deactivateForm(@AuthenticationPrincipal MemberPrincipal principal,
            RedirectAttributes redirectAttributes) {
        if (principal.isExternal()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "SSO 계정 비활성화는 관리자에게 요청해 주세요.");
            return "redirect:/dashboard/view";
        }
        return "member/deactivate";
    }

    @PostMapping("/deactivate")
    public String deactivateAccount(
            @Valid @ModelAttribute AccountDeactivationDto deactivationDto,
            BindingResult bindingResult,
            @AuthenticationPrincipal MemberPrincipal principal,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {
        if (principal.isExternal()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "SSO 계정 비활성화는 관리자에게 요청해 주세요.");
            return "redirect:/dashboard/view";
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/member/deactivate";
        }
        try {
            memberService.deactivateAccount(principal.getLoginId(), deactivationDto);
            clearLoginSession(request);
            redirectAttributes.addFlashAttribute(
                    "successMessage", "계정이 비활성화되었습니다.");
            return "redirect:/member/login";
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/member/deactivate";
        }
    }

    private void clearLoginSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
    }
}
