package com.deveopsj.market.namuh;

import java.time.*;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataAccessException;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.member.entity.Member;

@Controller @RequestMapping("/broker/namuh") @RequiredArgsConstructor
public class NamuhController {
    private static final String PREVIEW = "namuhHoldingPreview";
    private final NamuhService namuh;
    private final NamuhHoldingImportService imports;
    private final AssetPlanService plans;
    record Preview(Long memberId, String token, Instant expires, LocalDate date, List<NamuhHoldingImportService.Row> rows) {}

    @ModelAttribute
    void protect(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PREVIOUS_ADMINISTRATOR")))
            throw new AccessDeniedException("대리 로그인 중에는 증권사 연결을 사용할 수 없습니다.");
    }
    @GetMapping
    public String page(Model model, Member member) {
        model.addAttribute("ready", namuh.ready());
        model.addAttribute("connected", namuh.connected(member.getMemberId()));
        model.addAttribute("currentMonth", YearMonth.now());
        return "portfolio/namuh";
    }
    @PostMapping("/connect")
    public String connect(@RequestParam String appKey, @RequestParam String appSecret, Member member,
            HttpSession session, RedirectAttributes flash) {
        try {
            namuh.connect(member.getMemberId(), appKey, appSecret); session.removeAttribute(PREVIEW);
            flash.addFlashAttribute("message", "나무증권 연결이 확인되었습니다. 계좌 목록을 불러오세요.");
        } catch (IllegalArgumentException | IllegalStateException e) { flash.addFlashAttribute("errorMessage", e.getMessage()); }
        catch (DataAccessException e) { flash.addFlashAttribute("errorMessage", "연결정보 저장에 실패했습니다. 잠시 후 다시 시도하세요."); }
        return "redirect:/broker/namuh";
    }
    @PostMapping("/disconnect")
    public String disconnect(Member member, HttpSession session, RedirectAttributes flash) {
        namuh.disconnect(member.getMemberId()); session.removeAttribute(PREVIEW);
        flash.addFlashAttribute("message", "앱에 저장된 키와 토큰을 삭제했습니다. 나무 서비스 해지나 키 폐기는 나무 PLUG에서 진행하세요.");
        return "redirect:/broker/namuh";
    }
    @PostMapping("/accounts")
    public String accounts(Member member, Model model) {
        try { model.addAttribute("accounts", namuh.accounts(member.getMemberId())); }
        catch (IllegalStateException e) { model.addAttribute("errorMessage", e.getMessage()); }
        return page(model, member);
    }
    @PostMapping("/balances")
    public String balances(@RequestParam String account, @RequestParam String market, Member member,
            Model model, HttpSession session) {
        session.removeAttribute(PREVIEW);
        try {
            var rows = imports.preview(namuh.balances(member.getMemberId(), account, market));
            var preview = new Preview(member.getMemberId(), UUID.randomUUID().toString(), Instant.now().plusSeconds(600), LocalDate.now(), rows);
            session.setAttribute(PREVIEW, preview);
            model.addAttribute("balanceRows", rows); model.addAttribute("previewToken", preview.token());
            model.addAttribute("plans", plans.getPlansByMember(member));
            model.addAttribute("queriedAt", LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) { model.addAttribute("errorMessage", e.getMessage()); }
        return page(model, member);
    }
    @PostMapping("/apply")
    public String apply(@RequestParam String token, @RequestParam Long planId,
            @RequestParam(required = false) List<Integer> selected, Member member, HttpSession session, RedirectAttributes flash) {
        synchronized (session) {
            try {
                var preview = (Preview) session.getAttribute(PREVIEW);
                if (preview == null || !preview.memberId().equals(member.getMemberId()) || !preview.token().equals(token)
                        || Instant.now().isAfter(preview.expires()) || !LocalDate.now().equals(preview.date())
                        || !namuh.connected(member.getMemberId())) throw new IllegalArgumentException("미리보기가 만료되었습니다. 잔고를 다시 조회하세요.");
                imports.apply(member, planId, preview.rows(), selected);
                session.removeAttribute(PREVIEW);
                flash.addFlashAttribute("message", "선택한 보유분을 반영했습니다. 조회되지 않거나 선택하지 않은 기존 종목은 유지됩니다.");
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException e) {
                flash.addFlashAttribute("errorMessage", "보유분 반영에 실패했습니다. 플랜·종목·수량을 확인하고 다시 조회하세요.");
            }
        }
        return "redirect:/broker/namuh";
    }
    @PostMapping("/transactions")
    public String transactions(@RequestParam String account, @RequestParam YearMonth month,
            @RequestParam String market, Member member, Model model) {
        try {
            model.addAttribute("transactions", namuh.transactions(member.getMemberId(), account, month, market));
            model.addAttribute("selectedMonth", month); model.addAttribute("queriedAt", LocalDateTime.now());
        } catch (IllegalArgumentException | IllegalStateException e) { model.addAttribute("errorMessage", e.getMessage()); }
        return page(model, member);
    }
}
