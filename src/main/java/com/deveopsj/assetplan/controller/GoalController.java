package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.deveopsj.assetplan.entity.Goal;
import com.deveopsj.assetplan.service.GoalService;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/goal")
@RequiredArgsConstructor
public class GoalController {

    private final GoalService goalService;

    @GetMapping("/form")
    public String goalForm(Model model, Member member) {
        model.addAttribute("goals", goalService.getGoalsByMember(member));
        return "goal/form";
    }

    @PostMapping("/save")
    public String save(Goal goal, Member member) {
        goalService.save(goal, member);
        return "redirect:/goal/form";
    }
}
