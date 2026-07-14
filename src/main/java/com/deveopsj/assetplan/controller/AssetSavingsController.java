package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.entity.AssetSavings;
import com.deveopsj.assetplan.repository.AssetPlanRepository;
import com.deveopsj.assetplan.repository.AssetSavingsRepository;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/savings")
@RequiredArgsConstructor
public class AssetSavingsController {

    private final AssetSavingsRepository assetSavingsRepository;
    private final AssetPlanRepository assetPlanRepository;

    @GetMapping("/form")
    public String savingsForm(Model model) {
        // 계획 목록을 조회하여 선택할 수 있도록 모델에 추가
        model.addAttribute("plans", assetPlanRepository.findAll());
        return "savings/form";
    }

    @PostMapping("/save")
    public String save(AssetSavings assetSavings) {
        assetSavingsRepository.save(assetSavings);
        return "redirect:/dashboard/view";
    }
}
