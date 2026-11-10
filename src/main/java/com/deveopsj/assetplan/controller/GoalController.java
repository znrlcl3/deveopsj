package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.dto.GoalSaveRequest;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;
    private final com.deveopsj.assetplan.service.GoalProgressService goalProgressService;

    private final com.deveopsj.assetplan.service.GoalValuationProgressService goalValuationProgressService;

    @GetMapping("/form")
    public String goalForm(Model model, Member member) {
        var goals = goalService.getGoalsByMember(member);
        var progress = goalProgressService.summarize(member, goals);
        model.addAttribute("goals", goals);
        model.addAttribute("goalProgress", progress);
        model.addAttribute("valuationProgress", goalValuationProgressService.summarize(member, goals));
        model.addAttribute("reachedCount", progress.stream().filter(g -> g.remainingAmount() == 0).count());
        if (!model.containsAttribute("goalRequest")) model.addAttribute("goalRequest", new GoalSaveRequest());
        return "goal/form";
    }

    @PostMapping("/save")
    public String save(@Valid GoalSaveRequest request, BindingResult bindingResult, Member member,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            redirectAttributes.addFlashAttribute("goalRequest", request);
            return "redirect:/goal/form";
        }
        goalService.save(request, member);
        redirectAttributes.addFlashAttribute("message", "목표가 저장되었습니다.");
        return "redirect:/goal/form";
    }
}
