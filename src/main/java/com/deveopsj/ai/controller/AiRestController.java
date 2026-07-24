package com.deveopsj.ai.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.ai.dto.SpendingAnalysisRequest;
import com.deveopsj.ai.service.SpendingAnalysisService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiRestController {

    private final SpendingAnalysisService spendingAnalysisService;

    @PostMapping("/spending-analysis")
    public Map<String, String> analyzeSpending(
            @Valid @RequestBody SpendingAnalysisRequest request, Member member) {
        String feedback = spendingAnalysisService.analyze(request, member);
        return Map.of("feedback", feedback);
    }
}
