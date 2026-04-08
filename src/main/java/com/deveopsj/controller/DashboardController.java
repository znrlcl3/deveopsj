package com.deveopsj.controller;

import com.deveopsj.entity.AssetPlan;
import com.deveopsj.repository.AssetPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final AssetPlanRepository assetPlanRepository;

    @GetMapping("/api/test")
    public List<AssetPlan> testApi() {
        return assetPlanRepository.findAll(); 
    }
}