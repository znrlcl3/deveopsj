package com.deveopsj.assetplan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.deveopsj.assetplan.dto.AssetValuationSaveRequest;
import com.deveopsj.assetplan.service.AssetValuationService;
import com.deveopsj.member.entity.Member;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/valuations")
@RequiredArgsConstructor
public class AssetValuationController {

    private final AssetValuationService assetValuationService;

    @PostMapping("/save")
    public String save(@Valid AssetValuationSaveRequest request, BindingResult bindingResult,
            Member member, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    bindingResult.getAllErrors().get(0).getDefaultMessage());
            return "redirect:/trades/form";
        }

        try {
            assetValuationService.save(request, member);
            redirectAttributes.addFlashAttribute("message", "자산 평가금액이 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/trades/form";
    }
}
