package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/savings")
@RequiredArgsConstructor
public class AssetSavingsController {

    private final AssetSavingsService assetSavingsService;
    private final AssetPlanService assetPlanService;

    @GetMapping("/form")
    public String savingsForm(Model model, Member member) {
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        model.addAttribute("savings", assetSavingsService.getSavingsByMember(member));
        return "savings/form";
    }

    @PostMapping("/save")
    public String save(AssetSavings assetSavings, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        assetSavingsService.save(assetSavings);
        redirectAttributes.addFlashAttribute("message", "적립 내역이 저장되었습니다.");
        return "redirect:/savings/form";
    }
}
