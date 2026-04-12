package com.deveopsj.ai.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.dashboard.service.DashboardService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRestController {

    private final DashboardService dashboardService;

    @GetMapping("/analyze")
    public Map<String, String> getAnalysis(@RequestParam Long memberId) {
        var summary = dashboardService.getMonthlySummary(memberId);
        String feedback = summary.getAiBriefing(); 
        
        return Map.of("feedback", feedback);
    }
}