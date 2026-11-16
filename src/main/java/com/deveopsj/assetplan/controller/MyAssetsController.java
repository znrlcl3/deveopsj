package com.deveopsj.assetplan.controller;

import com.deveopsj.assetplan.dto.AssetBalanceRequest;
import com.deveopsj.assetplan.entity.AssetBalance;
import com.deveopsj.assetplan.service.AssetBalanceService;
import com.deveopsj.market.dto.PortfolioPosition;
import com.deveopsj.market.service.PortfolioValuationService;
import com.deveopsj.member.entity.Member;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

@Controller @RequiredArgsConstructor @RequestMapping("/assets")
public class MyAssetsController {
    private final AssetBalanceService balances;
    private final PortfolioValuationService portfolio;

    @GetMapping
    public String index(Member member, Model model) {
        var rows = balances.list(member.getMemberId());
        model.addAttribute("balances", rows);
        model.addAttribute("depositTotal", rows.stream().filter(b -> b.getKind() != AssetBalance.Kind.CASH)
                .map(b -> BigInteger.valueOf(b.getAmount())).reduce(BigInteger.ZERO, BigInteger::add));
        model.addAttribute("cashTotal", rows.stream().filter(b -> b.getKind() == AssetBalance.Kind.CASH)
                .map(b -> BigInteger.valueOf(b.getAmount())).reduce(BigInteger.ZERO, BigInteger::add));
        try {
            var summary = portfolio.getPortfolio(member);
            model.addAttribute("summary", summary);
            model.addAttribute("investmentGroups", summary.positions().stream()
                    .collect(Collectors.groupingBy(PortfolioPosition::assetPlanId, java.util.LinkedHashMap::new, Collectors.toList())).values().stream()
                    .map(positions -> new InvestmentGroup(positions.get(0).planName(), positions)).toList());
        } catch (IllegalStateException | ArithmeticException e) {
            model.addAttribute("portfolioError", "증권 평가를 불러오지 못했습니다. 보유분과 시세를 확인한 후 다시 시도해 주세요.");
            model.addAttribute("investmentGroups", List.of());
        }
        if (!model.containsAttribute("balance")) model.addAttribute("balance", new AssetBalanceRequest());
        model.addAttribute("kinds", AssetBalance.Kind.values());
        return "assets/list";
    }
    public record InvestmentGroup(String name, List<PortfolioPosition> positions) {}

    @GetMapping("/edit")
    public String edit(@RequestParam Long id, Member member, Model model, RedirectAttributes flash) {
        try {
            var row = balances.get(id, member.getMemberId());
            var request = new AssetBalanceRequest();
            request.setId(row.getId()); request.setVersion(row.getVersion()); request.setName(row.getName());
            request.setKind(row.getKind()); request.setAmount(row.getAmount());
            request.setAsOfDate(row.getAsOfDate()); request.setMaturityDate(row.getMaturityDate());
            model.addAttribute("balance", request);
            return index(member, model);
        } catch (IllegalArgumentException e) {
            flash.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/assets";
        }
    }
    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("balance") AssetBalanceRequest request, BindingResult errors,
                       Member member, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) {
            model.addAttribute("errorMessage", "이름, 자산 종류, 0 이상의 원화 잔액과 오늘까지의 기준일을 확인해 주세요.");
            return index(member, model);
        }
        try {
            balances.save(request, member.getMemberId());
            flash.addFlashAttribute("message", "현재 잔액을 저장했습니다. 수입·지출·납입 내역은 변경되지 않습니다.");
        } catch (IllegalArgumentException | OptimisticLockingFailureException e) {
            model.addAttribute("errorMessage", "저장할 수 없습니다. 자산이 삭제되거나 수정되었는지 새로고침 후 확인해 주세요.");
            return index(member, model);
        }
        return "redirect:/assets";
    }
    @PostMapping("/delete")
    public String delete(@RequestParam Long id, @RequestParam Long version, Member member, RedirectAttributes flash) {
        try {
            balances.delete(id, version, member.getMemberId());
            flash.addFlashAttribute("message", "직접 등록한 잔액을 삭제했습니다.");
        } catch (IllegalArgumentException | OptimisticLockingFailureException e) {
            flash.addFlashAttribute("errorMessage", "삭제할 수 없습니다. 새로고침 후 자산을 확인해 주세요.");
        }
        return "redirect:/assets";
    }
}
