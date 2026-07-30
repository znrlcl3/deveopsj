package com.deveopsj.market.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.market.dto.MarketPriceQuote;
import com.deveopsj.market.service.KisMarketPriceService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/market-prices")
@RequiredArgsConstructor
public class MarketPriceController {

    private final KisMarketPriceService kisMarketPriceService;

    @GetMapping("/{investmentAssetId}")
    public ResponseEntity<MarketPriceQuote> getQuote(@PathVariable Long investmentAssetId) {
        return ResponseEntity.ok(kisMarketPriceService.getQuote(investmentAssetId));
    }
}
