package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.entity.AssetPlan;
import com.deveopsj.assetplan.service.AssetPlanService;

import lombok.RequiredArgsConstructor;

import org.springframework.ui.Model;
import com.deveopsj.common.service.MasterCodeService;

@Controller
@RequestMapping("/assetplan")
@RequiredArgsConstructor
public class AssetPlanController {

    private final AssetPlanService assetPlanService;
    private final MasterCodeService masterCodeService;

    @GetMapping("/form")
    public String assetPlanForm(Model model) {
        model.addAttribute("codeMap", masterCodeService.getAllActiveCodesGrouped());
        return "assetplan/form";
    }

    @PostMapping("/save")
    public String save(AssetPlan assetPlan) {
        // TODO: 로그인 기능 구현 전이므로 임시로 MemberId는 하드코딩
        // assetPlan.setMember(memberRepository.findById(1L).orElseThrow());
        assetPlanService.save(assetPlan);
        return "redirect:/dashboard/view";
    }
}
