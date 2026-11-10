package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.service.*;
import com.deveopsj.member.entity.Member;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.*;
import java.time.*;
import java.util.UUID;

@Controller @RequiredArgsConstructor @RequestMapping("/holdings/import")
public class BalanceSnapshotController {
    private static final String KEY="balanceSnapshotPreview";
    private final BalanceSnapshotService balances;
    private final AssetPlanService plans;
    record Pending(Long memberId,String token,Instant expires,BalanceSnapshotService.Preview preview) {}
    @ModelAttribute void noCache(HttpServletResponse response) { response.setHeader("Cache-Control","no-store"); }
    @GetMapping public String form(Member member,Model model) {
        model.addAttribute("plans",plans.getPlansByMember(member));model.addAttribute("today",LocalDate.now());
        model.addAttribute("balances",balances.report(member.getMemberId()));return "portfolio/balance-import";
    }
    @PostMapping("/preview") public String preview(@RequestParam Long planId,@RequestParam LocalDate date,@RequestParam MultipartFile file,Member member,HttpSession session,Model model) {
        synchronized(session) {
            session.removeAttribute(KEY);
            try {
                if(file.isEmpty() || file.getSize()>NamuhTradeFileParser.MAX_BYTES) throw new IllegalArgumentException("5MB 이하의 파일을 선택하세요.");
                var p=balances.preview(planId,date,file.getBytes(),file.getOriginalFilename(),member.getMemberId());
                var pending=new Pending(member.getMemberId(),UUID.randomUUID().toString(),Instant.now().plusSeconds(600),p);
                session.setAttribute(KEY,pending);model.addAttribute("preview",p);model.addAttribute("token",pending.token());
            } catch(IllegalArgumentException e) { model.addAttribute("errorMessage",e.getMessage()); }
            catch(java.io.IOException e) { model.addAttribute("errorMessage","파일을 읽지 못했습니다."); }
        }
        return form(member,model);
    }
    @PostMapping("/save") public String save(@RequestParam String token,@RequestParam(defaultValue="false") boolean confirmed,Member member,HttpSession session,RedirectAttributes flash) {
        synchronized(session) {
            try {
                var pending=(Pending)session.getAttribute(KEY);
                if(pending==null || !pending.memberId().equals(member.getMemberId()) || !pending.token().equals(token) || Instant.now().isAfter(pending.expires())) throw new IllegalArgumentException("미리보기가 만료되었습니다. 다시 업로드하세요.");
                balances.save(pending.preview(),member.getMemberId(),confirmed);session.removeAttribute(KEY);
                flash.addFlashAttribute("message","잔고를 저장했습니다. 파일 기준 금액이며 기존 매매내역과 중복 합산하지 않습니다.");
            } catch(IllegalArgumentException e) { flash.addFlashAttribute("errorMessage",e.getMessage()); }
            catch(org.springframework.dao.DataAccessException e) { flash.addFlashAttribute("errorMessage","잔고 DB 저장에 실패했습니다. DB 설정을 확인한 뒤 다시 업로드하세요."); }
        }
        return "redirect:/holdings/import";
    }
    @GetMapping("/{id}/original") public ResponseEntity<byte[]> original(@PathVariable Long id,Member member) {
        var s=balances.owned(id,member.getMemberId());
        return ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff")
            .header("Content-Disposition",ContentDisposition.attachment().filename(s.getFileName(),java.nio.charset.StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.APPLICATION_OCTET_STREAM).body(s.getOriginalFile());
    }
}
