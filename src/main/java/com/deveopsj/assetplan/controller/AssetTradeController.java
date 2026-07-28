package com.deveopsj.assetplan.controller;

import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.dto.AssetTradeUpdateRequest;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.assetplan.service.AssetValuationService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/trades")
@RequiredArgsConstructor
public class AssetTradeController {

    private final AssetTradeService assetTradeService;
    private final AssetPlanService assetPlanService;
    private final AssetValuationService assetValuationService;

    @GetMapping("/form")
    public String tradeForm(Model model, Member member) {
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        model.addAttribute("valuations", assetValuationService.getValuationsByMember(member));
        return "trades/form";
    }

    @GetMapping("/list")
    public String tradeList(@RequestParam(required = false) YearMonth month,
            Model model, Member member) {
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        model.addAttribute("trades", assetTradeService.getTradesByMemberAndMonth(member, selectedMonth));
        model.addAttribute("selectedMonth", selectedMonth);
        return "trades/list";
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

    @PostMapping("/update")
    public String update(@Valid AssetTradeUpdateRequest request, BindingResult bindingResult,
            @RequestParam YearMonth month, Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/trades/list?month=" + month;
        }
        try {
            assetTradeService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 수정되었습니다.");
        } catch (IllegalArgumentException | ArithmeticException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "수정할 매매 내역을 확인해 주세요.");
        }
        return "redirect:/trades/list?month=" + month;
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, @RequestParam YearMonth month,
            Member member, RedirectAttributes redirectAttributes) {
        try {
            assetTradeService.delete(id, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/trades/list?month=" + month;
    }
}
