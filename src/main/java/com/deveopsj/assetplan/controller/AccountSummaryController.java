package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.service.AccountSummaryService;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

@Controller @RequiredArgsConstructor
public class AccountSummaryController {
    private final AccountSummaryService service;
    private final com.deveopsj.assetplan.service.BalanceSnapshotService balances;
    @org.springframework.web.bind.annotation.PostMapping("/assets/accounts/foreign/delete")
    public String deleteForeign(@RequestParam Long id,Member member,org.springframework.web.servlet.mvc.support.RedirectAttributes flash) {
        try { service.deleteForeignEntry(id,member.getMemberId()); } catch(IllegalArgumentException e) { flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/assets/accounts";
    }
    @GetMapping("/assets/accounts")
    public String summary(Member member, @RequestParam(required=false) String month, Model model) {
        YearMonth selected = YearMonth.now();
        try {
            if (month != null && !month.isBlank()) selected = YearMonth.parse(month);
            if (selected.getYear() < 1900 || selected.isAfter(YearMonth.now())) throw new IllegalArgumentException();
        } catch (DateTimeParseException | IllegalArgumentException e) {
            selected = YearMonth.now();
            model.addAttribute("errorMessage", "조회 월을 확인해 주세요. 이번 달 내역을 표시합니다.");
        }
        model.addAttribute("selectedMonth", selected);
        model.addAttribute("currentMonth", YearMonth.now());
        model.addAttribute("report", service.summarize(member, selected));
        model.addAttribute("balances", balances.report(member.getMemberId()));
        return "assets/accounts";
    }
}
