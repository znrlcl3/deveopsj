package com.deveopsj.dashboard.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.dashboard.dto.DashboardSummary;
import com.deveopsj.dashboard.service.DashboardService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> getSummary(Member member) {
        return ResponseEntity.ok(dashboardService.getMonthlySummary(member.getMemberId()));
    }
}
