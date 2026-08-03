package com.deveopsj.spending.controller;

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

import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.RecurringExpenseRequest;
import com.deveopsj.spending.service.RecurringExpenseService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public String list(Model model, Member member) {
        model.addAttribute("rules", recurringExpenseService.getRules(member));
        model.addAttribute("request", new RecurringExpenseRequest());
        return "spending/recurring-expenses";
    }

    @PostMapping
    public String save(@Valid RecurringExpenseRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/recurring-expenses";
        }
        try {
            recurringExpenseService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "고정지출을 등록했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-expenses";
    }

    @PostMapping("/update")
    public String update(@Valid RecurringExpenseRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/recurring-expenses";
        }
        try {
            recurringExpenseService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "고정지출을 수정했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-expenses";
    }

    @PostMapping("/toggle")
    public String toggle(@RequestParam Long id, Member member, RedirectAttributes redirectAttributes) {
        try {
            recurringExpenseService.toggle(id, member);
            redirectAttributes.addFlashAttribute("message", "고정지출 상태를 변경했습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/recurring-expenses";
    }

    @PostMapping("/confirm")
    public String confirm(@RequestParam Long id, @RequestParam String month,
            Member member, RedirectAttributes redirectAttributes) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            recurringExpenseService.confirm(id, selectedMonth, member);
            redirectAttributes.addFlashAttribute("message", "고정지출을 실제 지출로 확정했습니다.");
            redirectAttributes.addAttribute("month", selectedMonth);
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "조회 월 형식이 올바르지 않습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addAttribute("month", month);
        }
        return "redirect:/spending/list";
    }

    @PostMapping("/confirm-all")
    public String confirmAll(@RequestParam String month, Member member,
            RedirectAttributes redirectAttributes) {
        try {
            YearMonth selectedMonth = YearMonth.parse(month);
            int confirmedCount = recurringExpenseService.confirmAll(selectedMonth, member);
            redirectAttributes.addFlashAttribute("message",
                    confirmedCount == 0
                            ? "확정할 고정지출이 없습니다."
                            : "고정지출 " + confirmedCount + "건을 실제 지출로 확정했습니다.");
            redirectAttributes.addAttribute("month", selectedMonth);
        } catch (DateTimeParseException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "조회 월 형식이 올바르지 않습니다.");
        }
        return "redirect:/spending/list";
    }
}
