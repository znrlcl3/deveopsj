package com.deveopsj.market.controller;

import java.time.YearMonth;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioValuationService portfolioValuationService;

    @GetMapping
    public String portfolio(Model model, Member member) {
        model.addAttribute("summary", portfolioValuationService.getPortfolio(member));
        return "portfolio/list";
    }

    @GetMapping("/monthly")
    public String monthlyPortfolio(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            Model model,
            Member member) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth selectedMonth = month == null || month.isAfter(currentMonth)
                ? currentMonth : month;
        model.addAttribute("currentMonth", currentMonth);
        model.addAttribute(
                "summary", portfolioValuationService.getMonthlyPortfolio(member, selectedMonth));
        return "portfolio/monthly";
    }
}
