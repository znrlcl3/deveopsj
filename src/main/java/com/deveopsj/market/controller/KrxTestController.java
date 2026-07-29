package com.deveopsj.market.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.market.dto.InvestmentAssetSyncResult;
import com.deveopsj.market.dto.KrxStockSnapshot;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.service.InvestmentAssetSyncService;
import com.deveopsj.market.service.KrxStockService;
import com.deveopsj.market.service.NasdaqDirectoryService;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/krx")
@RequiredArgsConstructor
public class KrxTestController {

    private final KrxStockService krxStockService;
    private final NasdaqDirectoryService nasdaqDirectoryService;
    private final InvestmentAssetSyncService investmentAssetSyncService;

    @GetMapping("/test")
    public String testPage(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "KOSPI") KrxMarket market,
            Model model) {
        LocalDate requestedDate = date == null ? LocalDate.now() : date;
        model.addAttribute("requestedDate", requestedDate);
        model.addAttribute("selectedMarket", market);
        model.addAttribute("markets", KrxMarket.values());
        try {
            if (market == KrxMarket.US) {
                java.util.List<com.deveopsj.market.dto.KrxStockItem> stocks =
                        nasdaqDirectoryService.fetchAll();
                model.addAttribute("baseDate", LocalDate.now());
                model.addAttribute("stocks", stocks.stream().limit(1_000).toList());
                model.addAttribute("totalCount", stocks.size());
                model.addAttribute("displayLimited", stocks.size() > 1_000);
                return "krx/test";
            }
            KrxStockSnapshot snapshot = krxStockService.fetchLatest(requestedDate, market);
            model.addAttribute("baseDate", snapshot.baseDate());
            model.addAttribute("stocks", snapshot.stocks());
            model.addAttribute("totalCount", snapshot.stocks().size());
            model.addAttribute("displayLimited", false);
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("stocks", java.util.List.of());
            model.addAttribute("totalCount", 0);
        }
        return "krx/test";
    }

    @PostMapping("/sync")
    public String synchronize(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam KrxMarket market,
            RedirectAttributes redirectAttributes) {
        LocalDate requestedDate = date == null ? LocalDate.now() : date;
        try {
            java.util.List<com.deveopsj.market.dto.KrxStockItem> stocks;
            if (market == KrxMarket.US) {
                stocks = nasdaqDirectoryService.fetchAll();
            } else {
                stocks = krxStockService.fetchLatest(requestedDate, market).stocks();
            }
            InvestmentAssetSyncResult result = investmentAssetSyncService.synchronize(stocks, market);
            redirectAttributes.addFlashAttribute("message",
                    "종목 마스터 동기화 완료: 신규 " + result.createdCount()
                            + "개, 갱신 " + result.updatedCount() + "개");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        redirectAttributes.addAttribute("date", requestedDate);
        redirectAttributes.addAttribute("market", market);
        return "redirect:/krx/test";
    }
}
