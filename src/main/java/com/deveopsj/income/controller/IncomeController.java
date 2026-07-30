package com.deveopsj.income.controller;

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

import com.deveopsj.income.dto.IncomeSaveRequest;
import com.deveopsj.income.dto.IncomeUpdateRequest;
import com.deveopsj.income.entity.Income.IncomeType;
import com.deveopsj.income.service.IncomeService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/income")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @GetMapping("/form")
    public String form(Model model) {
        model.addAttribute("incomeTypes", IncomeType.values());
        return "income/form";
    }

    @GetMapping("/list")
    public String list(@RequestParam(required = false) String month,
            Model model, Member member) {
        YearMonth selectedMonth = parseMonth(month, model);
        var incomes = incomeService.getIncomesByMemberAndMonth(member, selectedMonth);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("incomeTypes", IncomeType.values());
        model.addAttribute("incomes", incomes);
        model.addAttribute("totalAmount",
                incomes.stream().mapToLong(income -> income.getAmount()).sum());
        return "income/list";
    }

    @PostMapping("/save")
    public String save(@Valid IncomeSaveRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", firstError(bindingResult));
            return "redirect:/income/form";
        }
        incomeService.save(request, member);
        redirectAttributes.addFlashAttribute("message", "수입 내역이 저장되었습니다.");
        return "redirect:/income/list?month=" + YearMonth.from(request.getIncomeDate());
    }

    @PostMapping("/update")
    public String update(@Valid IncomeUpdateRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        YearMonth month = request.getIncomeDate() == null
                ? YearMonth.now() : YearMonth.from(request.getIncomeDate());
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", firstError(bindingResult));
            return "redirect:/income/list?month=" + month;
        }
        try {
            incomeService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "수입 내역이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/income/list?month=" + month;
    }

    @PostMapping("/delete")
    public String delete(Long id, String month, Member member,
            RedirectAttributes redirectAttributes) {
        try {
            incomeService.deleteById(id, member);
            redirectAttributes.addFlashAttribute("message", "수입 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/income/list?month=" + safeMonth(month);
    }

    private YearMonth parseMonth(String month, Model model) {
        if (month == null) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException e) {
            model.addAttribute(
                    "errorMessage", "조회 월 형식이 올바르지 않아 이번 달을 표시합니다.");
            return YearMonth.now();
        }
    }

    private YearMonth safeMonth(String month) {
        try {
            return YearMonth.parse(month);
        } catch (RuntimeException e) {
            return YearMonth.now();
        }
    }

    private String firstError(BindingResult bindingResult) {
        return bindingResult.getAllErrors().get(0).getDefaultMessage();
    }
}
