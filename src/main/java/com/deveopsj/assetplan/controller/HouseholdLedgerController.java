package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.dto.MonthlyDepositRequest;
import com.deveopsj.assetplan.service.HouseholdLedgerService;
import com.deveopsj.member.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;

@Controller @RequiredArgsConstructor
public class HouseholdLedgerController {
    private final HouseholdLedgerService ledger;
    @GetMapping("/assets")
    public String index(@RequestParam(required = false) String month, Model model, Member member) {
        YearMonth selected;
        try {
            selected = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
            if (selected.getYear() < 1900 || selected.isAfter(YearMonth.now())) throw new DateTimeException("month");
        } catch (DateTimeException e) {
            selected = YearMonth.now();
            model.addAttribute("errorMessage", "오늘까지의 조회 월을 선택하세요.");
        }
        model.addAttribute("ledger", ledger.summary(member.getMemberId(), selected));
        model.addAttribute("selectedMonth", selected);
        model.addAttribute("currentMonth", YearMonth.now());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("depositDate", selected.equals(YearMonth.now()) ? LocalDate.now() : selected.atEndOfMonth());
        return "assets/household";
    }
    @PostMapping("/assets/opening")
    public String opening(@RequestParam Long planId, @RequestParam Long amount,
            @RequestParam LocalDate date, @RequestParam(required=false) Long expectedAmount,
            @RequestParam(required=false) LocalDate expectedDate, Member member, RedirectAttributes flash) {
        try {
            ledger.opening(member.getMemberId(), planId, amount, date, expectedAmount, expectedDate);
            flash.addFlashAttribute("message", "시작 원금을 저장했습니다. 월별 납입·수입·지출에는 합산하지 않습니다.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/assets";
    }
    @PostMapping("/assets/monthly")
    public String monthly(@ModelAttribute MonthlyDepositRequest request, Member member, RedirectAttributes flash) {
        try {
            int count = ledger.confirmMonth(member.getMemberId(), request);
            flash.addFlashAttribute("message", count + "개 플랜의 실제 납입을 등록했습니다.");
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage", e.getMessage()); }
        return "redirect:/assets" + (request.getDate() == null ? "" : "?month=" + YearMonth.from(request.getDate()));
    }
}
