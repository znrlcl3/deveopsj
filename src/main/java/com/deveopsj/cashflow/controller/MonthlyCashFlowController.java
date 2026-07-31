package com.deveopsj.cashflow.controller;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.deveopsj.cashflow.service.MonthlyCashFlowService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/cashflow")
@RequiredArgsConstructor
public class MonthlyCashFlowController {

    private final MonthlyCashFlowService monthlyCashFlowService;

    @GetMapping
    public String cashFlow(@RequestParam(required = false) String month,
            Model model, Member member) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth selectedMonth = parseMonth(month, currentMonth, model);
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute(
                "summary", monthlyCashFlowService.getMonthlyCashFlow(member, selectedMonth));
        return "cashflow/monthly";
    }

    private YearMonth parseMonth(String month, YearMonth currentMonth, Model model) {
        if (month == null) {
            return currentMonth;
        }
        try {
            YearMonth parsed = YearMonth.parse(month);
            if (parsed.isAfter(currentMonth)) {
                model.addAttribute(
                        "errorMessage", "미래 월은 조회할 수 없어 이번 달을 표시합니다.");
                return currentMonth;
            }
            return parsed;
        } catch (DateTimeParseException e) {
            model.addAttribute(
                    "errorMessage", "조회 월 형식이 올바르지 않아 이번 달을 표시합니다.");
            return currentMonth;
        }
    }
}
