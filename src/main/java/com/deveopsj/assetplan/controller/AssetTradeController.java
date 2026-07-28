package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/trades")
@RequiredArgsConstructor
public class AssetTradeController {

    private final AssetTradeService assetTradeService;
    private final AssetPlanService assetPlanService;

    @GetMapping("/form")
    public String tradeForm(Model model, Member member) {
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        model.addAttribute("trades", assetTradeService.getTradesByMember(member));
        return "trades/form";
    }

    @PostMapping("/save")
    public String save(@Valid AssetTradeSaveRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/trades/form";
        }

        try {
            assetTradeService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 저장되었습니다.");
        } catch (IllegalArgumentException | ArithmeticException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "거래금액을 확인해 주세요.");
        }
        return "redirect:/trades/form";
    }
}
