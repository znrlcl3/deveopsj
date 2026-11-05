package com.deveopsj.spending.controller;

import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.dto.CardImportPreview;
import com.deveopsj.spending.service.CardCancellationService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller @RequiredArgsConstructor
@RequestMapping("/spending/card-cancellation")
public class CardCancellationController {
    private final CardCancellationService service;

    private CardImportPreview preview(HttpSession session, String token) {
        Object value = session.getAttribute("cardSpendingImportPreview");
        if (!(value instanceof CardImportPreview p) || !p.token().equals(token))
            throw new IllegalArgumentException("미리보기가 만료되었습니다. 파일을 다시 올려 주세요.");
        return p;
    }

    @GetMapping
    public String review(@RequestParam String token, @RequestParam int rowIndex,
            Member member, HttpSession session, Model model, RedirectAttributes redirect) {
        try {
            model.addAttribute("review", service.review(preview(session, token), rowIndex, member));
            model.addAttribute("token", token);
            model.addAttribute("rowIndex", rowIndex);
            return "spending/cancellation-review";
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/spending/list";
        }
    }

    @PostMapping
    public String apply(@RequestParam String token, @RequestParam int rowIndex,
            @RequestParam Long targetId, @RequestParam long expectedAmount,
            @RequestParam(defaultValue = "false") boolean reviewed, Member member,
            HttpSession session, RedirectAttributes redirect) {
        try {
            service.apply(preview(session, token), rowIndex, member, targetId, expectedAmount, reviewed);
            redirect.addFlashAttribute("message", "취소 반영을 확인했습니다. 같은 취소 내역은 다시 차감하지 않습니다.");
        } catch (IllegalArgumentException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        } catch (org.springframework.dao.DataIntegrityViolationException
                | org.springframework.orm.ObjectOptimisticLockingFailureException e) {
            redirect.addFlashAttribute("errorMessage", "다른 요청에서 내역이 변경됐습니다. 현재 처리 상태를 확인해 주세요.");
        }
        redirect.addAttribute("token", token);
        redirect.addAttribute("rowIndex", rowIndex);
        return "redirect:/spending/card-cancellation";
    }
}
