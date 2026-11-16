package com.deveopsj.market.controller;


import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.market.service.KisExchangeRateService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/portfolio")
@RequiredArgsConstructor
public class PortfolioController {

    private final PortfolioValuationService portfolioValuationService;
    private final KisExchangeRateService kisExchangeRateService;

    @GetMapping
    public String portfolio(Model model, Member member) {
        var summary=portfolioValuationService.getPortfolio(member);
        model.addAttribute("summary", summary);
        if(summary.snapshots().isEmpty() || !summary.positions().isEmpty()) {
        try {
            model.addAttribute("usdKrwRate", kisExchangeRateService.getKrwRate("USD"));
        } catch (IllegalStateException e) {
            model.addAttribute("exchangeRateError", e.getMessage());
        }
        }
        return "portfolio/list";
    }

    @GetMapping("/monthly")
    public String monthlyPortfolio() {
        return "redirect:/portfolio";
    }
}
