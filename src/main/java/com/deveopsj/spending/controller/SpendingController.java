package com.deveopsj.spending.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.service.SpendingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.deveopsj.member.entity.Member;

@Controller
@RequestMapping("/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final DataInputService dataInputService;
    private final com.deveopsj.spending.repository.DailySpendingRepository dailySpendingRepository;
    private final SpendingService spendingService;

    @GetMapping("/form")
    public String spendingForm(Model model, Member member) {
        model.addAttribute("spendings", dailySpendingRepository.findByMemberMemberIdOrderBySpendingDateDesc(member.getMemberId()));
        return "spending/form"; 
    }

    @PostMapping("/api/save")
    @ResponseBody
    public ResponseEntity<String> saveSpending(@Valid @RequestBody SpendingSaveRequest request, Member member) {
        dataInputService.saveSpendingWithAi(request, member);
        return ResponseEntity.ok("성공적으로 저장되었습니다.");
    }
    
    @PostMapping("/delete")
    public String delete(Long id, Member member, org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        try {
            spendingService.deleteById(id, member);
            redirectAttributes.addFlashAttribute("message", "지출 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/spending/form";
    }
}
