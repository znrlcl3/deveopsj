package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;

import com.deveopsj.assetplan.dto.AssetPlanSaveRequest;
import com.deveopsj.assetplan.dto.AssetPlanUpdateRequest;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/assetplan")
@RequiredArgsConstructor
public class AssetPlanController {

    private final AssetPlanService assetPlanService;
    private final MasterCodeService masterCodeService;
    private final GoalService goalService;

    @GetMapping("/form")
    public String assetPlanForm(Model model, Member member) {
        model.addAttribute("codeMap", masterCodeService.getAllActiveCodesGrouped());
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        return "assetplan/form";
    }

    @GetMapping("/list")
    public String assetPlanList(Model model, Member member) {
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        return "assetplan/list";
    }

    @PostMapping("/save")
    public String save(@Valid AssetPlanSaveRequest request, BindingResult bindingResult, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/assetplan/form";
        }
        try {
            assetPlanService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "자산 플랜이 성공적으로 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assetplan/form";
        }
        return "redirect:/assetplan/list";
    }

    @PostMapping("/update")
    public String update(@Valid AssetPlanUpdateRequest request, BindingResult bindingResult, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/assetplan/list";
        }
        try {
            assetPlanService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "자산 플랜이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/assetplan/list";
    }

    @PostMapping("/delete")
    public String delete(Long id, Member member, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            assetPlanService.deleteById(id, member);
            redirectAttributes.addFlashAttribute("message", "자산 플랜이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/assetplan/list";
    }
}
