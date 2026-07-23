package com.deveopsj.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.ai.config.GeminiProperties;
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
    public String dashboard(Model model, Member member) {
    	
        var summary = dashboardService.getMonthlySummary(member.getMemberId());
        model.addAttribute("summary", summary);
        model.addAttribute("aiModel", geminiProperties.getModel());
        return "dashboard";
    }
    
    @GetMapping("/input")
    public String inputPage() {
        return "input"; 
    }
}
