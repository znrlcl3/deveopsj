package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.dto.HoldingSaveRequest;
import com.deveopsj.assetplan.service.*;
import com.deveopsj.member.entity.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor @RequestMapping("/holdings")
public class HoldingController {
    private final HoldingService holdings;
    private final AssetPlanService plans;

    @GetMapping("/form")
    public String form(Model model, Member member) {
        model.addAttribute("plans", plans.getPlansByMember(member));
        if (!model.containsAttribute("holding")) model.addAttribute("holding", new HoldingSaveRequest());
        return "portfolio/holding-form";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("holding") HoldingSaveRequest request,
            BindingResult errors, Member member, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            flash.addFlashAttribute("errorMessage", "플랜·종목 선택과 수량·평균 매수가의 양수 및 소수 자릿수를 확인해 주세요.");
        } else {
            try {
                holdings.save(request, member);
                flash.addFlashAttribute("message", "현재 보유분이 반영되었습니다. 다음 종목을 등록하거나 보유자산 수익률을 확인하세요.");
                return "redirect:/holdings/form";
            } catch (IllegalArgumentException e) {
                flash.addFlashAttribute("errorMessage", e.getMessage());
            } catch (ArithmeticException e) {
                flash.addFlashAttribute("errorMessage", "계산된 원금이 허용 범위를 초과합니다. 수량·가격·환율을 확인해 주세요.");
            }
        }
        // Re-select the instrument after an error rather than showing a stale name for its ID.
        request.setInvestmentAssetId(null);
        flash.addFlashAttribute("holding", request);
        return "redirect:/holdings/form";
    }
}
