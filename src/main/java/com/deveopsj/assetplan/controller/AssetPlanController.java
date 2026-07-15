package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;

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

    @PostMapping("/save")
    public String save(AssetPlan assetPlan, Member member, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        assetPlan.setMember(member);
        assetPlanService.save(assetPlan);
        redirectAttributes.addFlashAttribute("message", "자산 플랜이 성공적으로 저장되었습니다.");
        return "redirect:/dashboard/view";
    }
}
