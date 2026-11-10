package com.deveopsj.assetplan.controller;

import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.assetplan.dto.AssetTradeSaveRequest;
import com.deveopsj.assetplan.dto.AssetTradeUpdateRequest;
import com.deveopsj.assetplan.service.AssetPlanService;
import com.deveopsj.assetplan.service.AssetTradeService;
import com.deveopsj.assetplan.service.AssetValuationService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/trades")
@RequiredArgsConstructor
public class AssetTradeController {

    private final com.deveopsj.assetplan.service.AccountTransferService accountTransfers;
    private final AssetTradeService assetTradeService;
    private final AssetPlanService assetPlanService;
    private final AssetValuationService assetValuationService;

    @GetMapping("/form")
    public String tradeForm(Model model, Member member) {
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        model.addAttribute("valuations", assetValuationService.getValuationsByMember(member));
        return "trades/form";
    }

    @GetMapping("/list")
    public String tradeList(@RequestParam(required = false) YearMonth month,
            @RequestParam(defaultValue="ALL") String type,
            Model model, Member member) {
        YearMonth selectedMonth = month == null ? YearMonth.now() : month;
        model.addAttribute("plans", assetPlanService.getPlansByMember(member));
        String selectedType = java.util.Set.of("ALL","BUY","SELL","DEPOSIT","WITHDRAWAL").contains(type) ? type : "ALL";
        model.addAttribute("selectedType",selectedType);
        model.addAttribute("transfers",accountTransfers.list(member.getMemberId(),selectedMonth).stream()
                .filter(t -> selectedType.equals("ALL") || t.getType().name().equals(selectedType)).toList());
        var records = assetTradeService.getTradesByMemberAndMonth(member, selectedMonth);
        model.addAttribute("trades", records.stream().filter(t -> t.getTradeType() != com.deveopsj.assetplan.entity.AssetTrade.TradeType.SNAPSHOT && (selectedType.equals("ALL") || t.getTradeType().name().equals(selectedType))).toList());
        model.addAttribute("holdingSnapshots", records.stream().filter(t -> t.getTradeType() == com.deveopsj.assetplan.entity.AssetTrade.TradeType.SNAPSHOT).toList());
        model.addAttribute("selectedMonth", selectedMonth);
        return "trades/list";
    }

    @PostMapping("/transfers/delete")
    public String deleteTransfer(@RequestParam Long id,@RequestParam YearMonth month,Member member,RedirectAttributes flash) {
        try { accountTransfers.delete(id,member.getMemberId()); flash.addFlashAttribute("message","입출금 기록을 삭제했습니다. 수입·지출과 보유수량은 변경되지 않습니다."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("errorMessage",e.getMessage()); }
        return "redirect:/trades/list?month="+month;
    }

    @PostMapping("/save")
    public String save(@Valid AssetTradeSaveRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/trades/form";
        }

        try {
            assetTradeService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ArithmeticException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "거래금액을 확인해 주세요.");
        }
        return "redirect:/trades/form";
    }

    @PostMapping("/update")
    public String update(@Valid AssetTradeUpdateRequest request, BindingResult bindingResult,
            @RequestParam YearMonth month, Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/trades/list?month=" + month;
        }
        try {
            assetTradeService.update(request, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 수정되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        } catch (ArithmeticException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "수정할 매매 내역을 확인해 주세요.");
        }
        return "redirect:/trades/list?month=" + month;
    }

    @PostMapping("/delete")
    public String delete(@RequestParam Long id, @RequestParam YearMonth month,
            Member member, RedirectAttributes redirectAttributes) {
        try {
            assetTradeService.delete(id, member);
            redirectAttributes.addFlashAttribute("message", "매매 내역이 삭제되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/trades/list?month=" + month;
    }
}
