package com.deveopsj.assetplan.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.assetplan.dto.RecurringSavingsRequest;
import com.deveopsj.assetplan.service.AssetSavingsService;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.assetplan.service.RecurringSavingsService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/recurring-savings")
@RequiredArgsConstructor
public class RecurringSavingsController {

    private final RecurringSavingsService recurringSavingsService;
    private final AssetSavingsService assetSavingsService;
    private final GoalService goalService;

    @GetMapping
    public String list(Model model, Member member) {
        model.addAttribute("rules", recurringSavingsService.getRules(member));
        model.addAttribute("plans", assetSavingsService.getDepositPlansByMember(member));
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        model.addAttribute("request", new RecurringSavingsRequest());
        return "savings/recurring-savings";
    }

    @PostMapping
    public String save(@Valid RecurringSavingsRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return validationError(bindingResult, redirectAttributes);
        }
        try {
            recurringSavingsService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "정기 납입을 등록했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-savings";
    }

    @PostMapping("/update")
    public String update(@Valid RecurringSavingsRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return validationError(bindingResult, redirectAttributes);
        }
        try {
            recurringSavingsService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "정기 납입을 수정했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-savings";
    }

    @PostMapping("/toggle")
    public String toggle(@RequestParam Long id, Member member, RedirectAttributes redirectAttributes) {
        try {
            recurringSavingsService.toggle(id, member);
            redirectAttributes.addFlashAttribute("message", "정기 납입 상태를 변경했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-savings";
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam Long id, @RequestParam String month,
            Member member, RedirectAttributes redirectAttributes) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            recurringSavingsService.confirm(id, selectedMonth, member);
            redirectAttributes.addFlashAttribute("message", "정기 납입을 실제 납입으로 확정했습니다.");
            redirectAttributes.addAttribute("month", selectedMonth);
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "조회 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addAttribute("month", month);
        }
        return "redirect:/savings/list";
    }

    @PostMapping("/confirm-all")
    public String confirmAll(@RequestParam String month, Member member,
            RedirectAttributes redirectAttributes) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            int count = recurringSavingsService.confirmAll(selectedMonth, member);
            redirectAttributes.addFlashAttribute("message",
                    count == 0 ? "확정할 정기 납입이 없습니다." : count + "건의 정기 납입을 확정했습니다.");
            redirectAttributes.addAttribute("month", selectedMonth);
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "조회 월 형식이 올바르지 않습니다.");
        }
        return "redirect:/savings/list";
    }

    private String validationError(BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage",
                bindingResult.getAllErrors().get(0).getDefaultMessage());
        return "redirect:/recurring-savings";
    }
}
