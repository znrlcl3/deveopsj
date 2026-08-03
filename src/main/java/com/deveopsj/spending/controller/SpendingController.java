package com.deveopsj.spending.controller;

import java.time.YearMonth;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.dto.SpendingUpdateRequest;
import com.deveopsj.spending.service.SpendingService;
import com.deveopsj.spending.service.RecurringExpenseService;
import com.deveopsj.spending.service.SpendingExcelImportService;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.deveopsj.member.entity.Member;

@Controller
@RequestMapping("/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final DataInputService dataInputService;
    private final SpendingService spendingService;
    private final RecurringExpenseService recurringExpenseService;
    private final SpendingExcelImportService spendingExcelImportService;

    @GetMapping("/form")
    public String spendingForm() {
        return "spending/form"; 
    }

    @GetMapping("/list")
    public String spendingList(@RequestParam(required = false) String month, Model model, Member member) {
        YearMonth selectedMonth;
        try {
            selectedMonth = month == null ? YearMonth.now() : YearMonth.parse(month);
        } catch (java.time.format.DateTimeParseException e) {
            selectedMonth = YearMonth.now();
            model.addAttribute("errorMessage", "조회 월 형식이 올바르지 않아 이번 달을 표시합니다.");
        }

        var spendings = spendingService.getSpendings(
                member, selectedMonth.atDay(1), selectedMonth.atEndOfMonth());
        model.addAttribute("spendings", spendings);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("totalAmount",
                spendings.stream().mapToLong(spending -> spending.getAmount()).sum());
        var recurringOccurrences = recurringExpenseService.getOccurrences(member, selectedMonth);
        model.addAttribute("recurringOccurrences", recurringOccurrences);
        model.addAttribute("scheduledRecurringAmount", recurringOccurrences.stream()
                .filter(occurrence -> !occurrence.confirmed())
                .mapToLong(occurrence -> occurrence.rule().getAmount())
                .sum());
        model.addAttribute("unconfirmedRecurringCount", recurringOccurrences.stream()
                .filter(occurrence -> !occurrence.confirmed())
                .count());
        return "spending/list";
    }

    @PostMapping("/api/save")
    @ResponseBody
    public ResponseEntity<String> saveSpending(@Valid @RequestBody SpendingSaveRequest request, Member member) {
        dataInputService.saveSpendingWithAi(request, member);
        return ResponseEntity.ok("성공적으로 저장되었습니다.");
    }

    @PostMapping("/update")
    public String update(@Valid SpendingUpdateRequest request, BindingResult bindingResult,
            @RequestParam(required = false) String month, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        preserveMonth(month, redirectAttributes);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/spending/list";
        }
        try {
            spendingService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "지출 내역이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/spending/list";
    }
    
    @PostMapping("/delete")
    public String delete(Long id, @RequestParam(required = false) String month, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        preserveMonth(month, redirectAttributes);
        try {
            spendingService.deleteById(id, member);
            redirectAttributes.addFlashAttribute("message", "지출 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/spending/list";
    }

    @PostMapping("/import")
    public String importExcel(@RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String month, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        preserveMonth(month, redirectAttributes);
        try {
            int count = spendingExcelImportService.importFile(file, member);
            redirectAttributes.addFlashAttribute("message", "지출 내역 " + count + "건을 등록했습니다.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/spending/list";
    }

    private void preserveMonth(String month,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (month == null) {
            return;
        }
        try {
            YearMonth.parse(month);
            redirectAttributes.addAttribute("month", month);
        } catch (java.time.format.DateTimeParseException ignored) {
            // 잘못된 월 값은 전달하지 않고 목록의 기본 월을 사용한다.
        }
    }
}
