package com.deveopsj.ai.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRestController {

    private final DashboardService dashboardService;
    private final AiService aiService;

    @GetMapping("/analyze")
    public Map<String, String> getAnalysis(Member member) {
        DashboardSummary summary = dashboardService.getMonthlySummary(member.getMemberId());
        String feedback = aiService.getWealthFeedback(summary);
        return Map.of("feedback", feedback);
    }
}
