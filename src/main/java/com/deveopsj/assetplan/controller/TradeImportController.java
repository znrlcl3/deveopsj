package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.service.*;
import com.deveopsj.member.entity.Member;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.*;
import java.util.*;

@Controller @RequiredArgsConstructor @RequestMapping("/trades/import")
public class TradeImportController {
    private static final String SESSION_KEY = "namuhTradeFilePreview";
    private final NamuhTradeImportService imports;
    private final AssetPlanService plans;
    record Pending(Long member, String token, Instant expires, NamuhTradeImportService.Preview preview) {}
    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
    @GetMapping
    public String form(Member member, Model model) {
        model.addAttribute("plans", plans.getPlansByMember(member));
        return "trades/import";
    }
    @PostMapping("/preview")
    public String preview(@RequestParam MultipartFile file, @RequestParam Long planId, Member member,
                          HttpSession session, Model model) {
        synchronized (session) {
            session.removeAttribute(SESSION_KEY);
            try {
                if (file.isEmpty() || file.getSize() > NamuhTradeFileParser.MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 파일을 선택하세요.");
                var result = imports.preview(file.getBytes(), planId, member.getMemberId());
                var pending = new Pending(member.getMemberId(), UUID.randomUUID().toString(), Instant.now().plusSeconds(600), result);
                session.setAttribute(SESSION_KEY, pending);
                model.addAttribute("preview", result); model.addAttribute("token", pending.token());
            } catch (IllegalArgumentException e) { model.addAttribute("errorMessage", e.getMessage()); }
            catch (java.io.IOException | DataAccessException e) { model.addAttribute("errorMessage", "파일 미리보기에 실패했습니다. 파일과 플랜을 확인하세요."); }
        }
        return form(member, model);
    }
    @PostMapping("/apply")
    public String apply(@RequestParam String token, @RequestParam(required = false) List<String> selected,
                        Member member, HttpSession session, RedirectAttributes flash) {
        synchronized (session) {
            try {
                var pending = (Pending) session.getAttribute(SESSION_KEY);
                if (pending == null || !pending.member().equals(member.getMemberId()) || !pending.token().equals(token)
                        || Instant.now().isAfter(pending.expires())) throw new IllegalArgumentException("미리보기가 만료되었습니다. 다시 업로드하세요.");
                if (selected == null || selected.size() > 2000) throw new IllegalArgumentException("저장할 거래를 선택하세요.");
                var choices = new ArrayList<NamuhTradeImportService.Selection>();
                for (String value : selected) {
                    if (value.isBlank()) continue;
                    if (!value.matches("[0-9]{1,4}:[0-9]{1,19}")) throw new IllegalArgumentException("선택한 종목이 올바르지 않습니다.");
                    String[] parts = value.split(":");
                    choices.add(new NamuhTradeImportService.Selection(Integer.parseInt(parts[0]), Long.parseLong(parts[1])));
                }
                int count = imports.apply(pending.preview(), choices, member.getMemberId());
                session.removeAttribute(SESSION_KEY);
                var savedRows = choices.stream().map(c -> pending.preview().rows().get(c.index()).source()).toList();
                long incomeCount = savedRows.stream().filter(NamuhTradeFileParser.Row::income).count();
                boolean onlyIncome = incomeCount == count;
                var month = savedRows.stream().filter(r -> onlyIncome || !r.income()).map(r -> YearMonth.from(r.date()))
                        .max(Comparator.naturalOrder()).orElse(YearMonth.now());
                flash.addFlashAttribute("message", "매매 " + (count - incomeCount) + "건, 수입 " + incomeCount + "건을 저장했습니다. 분배금·예탁금 이용료는 수입 관리에서 확인하세요.");
                return "redirect:" + (onlyIncome ? "/income/list" : "/trades/list") + "?month=" + month;
            } catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage", e.getMessage()); }
            catch (DataAccessException e) { flash.addFlashAttribute("errorMessage", "다른 작업과 충돌하거나 중복된 거래가 있습니다. 다시 업로드하세요."); }
        }
        return "redirect:/trades/import";
    }
}
