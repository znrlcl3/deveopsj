package com.deveopsj.dashboard.controller;

import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.deveopsj.ai.config.GeminiProperties;
import com.deveopsj.ai.dto.SpendingAnalysisType;
import com.deveopsj.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

import com.deveopsj.member.entity.Member;

@Controller
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardViewController {

    private final DashboardService dashboardService;
    private final GeminiProperties geminiProperties;

    @GetMapping("/view")
    public String dashboard(@RequestParam(required = false) String month, Model model, Member member) {
        YearMonth selectedMonth;
        try {
            selectedMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
            if (selectedMonth.getYear() < 1900 || selectedMonth.getYear() > 9999) {
                throw new java.time.DateTimeException("Unsupported year");
            }
        } catch (java.time.DateTimeException e) {
            selectedMonth = YearMonth.now();
            model.addAttribute("errorMessage", "조회 월 형식이 올바르지 않아 이번 달을 표시합니다.");
        }
        var summary = dashboardService.getMonthlySummary(member.getMemberId(), selectedMonth);
        model.addAttribute("summary", summary);
        model.addAttribute("aiModel", geminiProperties.getModel());
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("currentMonth", YearMonth.now());
        model.addAttribute("analysisMonth", selectedMonth.isAfter(YearMonth.now()) ? YearMonth.now() : selectedMonth);
        model.addAttribute("analysisTypes", SpendingAnalysisType.values());
        return "dashboard";
    }
    
    @GetMapping("/input")
    public String inputPage() {
        return "input"; 
    }
}
