package com.deveopsj.market.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.market.dto.InvestmentAssetSearchItem;
import com.deveopsj.market.service.InvestmentAssetService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/investment-assets")
@RequiredArgsConstructor
public class InvestmentAssetController {

    private final InvestmentAssetService investmentAssetService;

    @GetMapping("/search")
    public List<InvestmentAssetSearchItem> search(@RequestParam String keyword) {
        return investmentAssetService.search(keyword);
    }
}
