package com.deveopsj.assetplan.controller;

import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.dto.AssetSavingsSaveRequest;
import com.deveopsj.assetplan.dto.AssetSavingsUpdateRequest;
import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/savings")
@RequiredArgsConstructor
public class AssetSavingsController {

    private final AssetSavingsService assetSavingsService;
    private final GoalService goalService;

    @GetMapping("/form")
    public String savingsForm(Model model, Member member) {
        model.addAttribute("plans", assetSavingsService.getDepositPlansByMember(member));
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        return "savings/form";
    }

    @GetMapping("/list")
    public String savingsList(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model, Member member) {
        YearMonth selectedMonth = month != null ? month : YearMonth.now();
        var savings = assetSavingsService.getSavingsByMemberAndMonth(member, selectedMonth);
        long totalAmount = savings.stream().mapToLong(saving -> saving.getAmount()).sum();

        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("plans", assetSavingsService.getDepositPlansByMember(member));
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        model.addAttribute("savings", savings);
        model.addAttribute("totalAmount", totalAmount);
        return "savings/list";
    }

    @PostMapping("/save")
    public String save(@Valid AssetSavingsSaveRequest request, BindingResult bindingResult, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/savings/form";
        }
        try {
            assetSavingsService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "적립 내역이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/savings/list";
    }

    @PostMapping("/update")
    public String update(@Valid AssetSavingsUpdateRequest request, BindingResult bindingResult,
            Member member, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        YearMonth month = request.getDepositDate() != null
                ? YearMonth.from(request.getDepositDate()) : YearMonth.now();
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/savings/list?month=" + month;
        }
        try {
            assetSavingsService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "납입 내역이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/savings/list?month=" + month;
    }

    @PostMapping("/delete")
    public String delete(Long id, String month, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            assetSavingsService.deleteById(id, member);
            redirectAttributes.addFlashAttribute("message", "납입 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        YearMonth selectedMonth;
        try {
            selectedMonth = YearMonth.parse(month);
        } catch (RuntimeException e) {
            selectedMonth = YearMonth.now();
        }
        return "redirect:/savings/list?month=" + selectedMonth;
    }
}
