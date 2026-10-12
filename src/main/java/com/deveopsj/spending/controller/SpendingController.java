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
import org.springframework.web.bind.annotation.ModelAttribute;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.dto.SpendingUpdateRequest;
import com.deveopsj.spending.service.SpendingService;
import com.deveopsj.spending.service.RecurringExpenseService;
import com.deveopsj.spending.service.SpendingExcelImportService;
import com.deveopsj.spending.dto.CardCompany;
import com.deveopsj.spending.dto.CardImportPreview;
import com.deveopsj.spending.service.CardSpendingImportService;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpSession;

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
    private final CardSpendingImportService cardSpendingImportService;

    private static final String CARD_IMPORT_PREVIEW = "cardSpendingImportPreview";

    @ModelAttribute("cardCompanies")
    public CardCompany[] cardCompanies() {
        return CardCompany.values();
    }

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
        long totalAmount = spendings.stream().mapToLong(spending -> spending.getAmount()).sum();
        model.addAttribute("spendings", spendings);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("totalAmount", totalAmount);
        var recurringOccurrences = recurringExpenseService.getOccurrences(member, selectedMonth);
        long scheduledRecurringAmount = recurringOccurrences.stream()
                .filter(occurrence -> !occurrence.confirmed())
                .mapToLong(occurrence -> occurrence.rule().getAmount())
                .sum();
        model.addAttribute("recurringOccurrences", recurringOccurrences);
        model.addAttribute("scheduledRecurringAmount", scheduledRecurringAmount);
        model.addAttribute("expectedTotalAmount", totalAmount + scheduledRecurringAmount);
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

    @PostMapping("/card-import/preview")
    public String previewCardImport(@RequestParam CardCompany cardCompany,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String filePassword,
            Model model, HttpSession session,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            CardImportPreview preview = cardSpendingImportService.preview(cardCompany, file, filePassword);
            session.setAttribute(CARD_IMPORT_PREVIEW, preview);
            model.addAttribute("preview", preview);
            return "spending/import-preview";
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/spending/list";
        }
    }

    @PostMapping("/card-import/confirm")
    public String confirmCardImport(@RequestParam String token,
            @RequestParam(required = false) java.util.List<Integer> selectedRows,
            @RequestParam java.util.Map<String, String> parameters,
            Member member, HttpSession session, Model model,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        Object stored = session.getAttribute(CARD_IMPORT_PREVIEW);
        if (!(stored instanceof CardImportPreview preview) || !preview.token().equals(token)) {
            redirectAttributes.addFlashAttribute("errorMessage", "미리보기 정보가 만료되었습니다. 파일을 다시 선택해 주세요.");
            return "redirect:/spending/list";
        }
        java.util.Map<Integer, String> categories = new java.util.HashMap<>();
        for (var entry : parameters.entrySet()) {
            if (entry.getKey().startsWith("category_")) {
                try {
                    categories.put(Integer.parseInt(entry.getKey().substring(9)), entry.getValue());
                } catch (NumberFormatException ignored) {
                    // 알 수 없는 폼 필드는 사용하지 않는다.
                }
            }
        }
        try {
            var result = cardSpendingImportService.confirm(preview, selectedRows, categories, member);
            session.removeAttribute(CARD_IMPORT_PREVIEW);
            redirectAttributes.addFlashAttribute("message", "카드 지출 " + result.savedCount()
                    + "건을 등록했습니다." + (result.duplicateCount() > 0
                            ? " 중복 " + result.duplicateCount() + "건은 제외했습니다." : ""));
        } catch (IllegalArgumentException | IllegalStateException e) {
            model.addAttribute("preview", preview);
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("selectedRows", selectedRows == null ? java.util.List.of() : selectedRows);
            model.addAttribute("selectedCategories", categories);
            return "spending/import-preview";
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
