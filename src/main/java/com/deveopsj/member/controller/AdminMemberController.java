package com.deveopsj.member.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.member.security.CustomUserDetails;
import com.deveopsj.member.service.MemberService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/admin/members")
@RequiredArgsConstructor
public class AdminMemberController {

    private final MemberService memberService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("members", memberService.getMembersForAdmin());
        return "member/admin-list";
    }

    @PostMapping("/{memberId}/status")
    public String changeStatus(
            @PathVariable Long memberId,
            @RequestParam boolean active,
            @AuthenticationPrincipal CustomUserDetails userDetails,
            RedirectAttributes redirectAttributes) {
        try {
            memberService.changeMemberActiveStatus(
                    userDetails.getUsername(), memberId, active);
            redirectAttributes.addFlashAttribute(
                    "successMessage", active ? "계정을 활성화했습니다." : "계정을 비활성화했습니다.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/admin/members";
    }

}
