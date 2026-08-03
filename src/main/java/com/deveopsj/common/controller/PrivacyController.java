package com.deveopsj.common.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PrivacyController {

    private final String operatorName;
    private final String contactEmail;

    public PrivacyController(
            @Value("${app.privacy.operator-name:서비스 운영자}") String operatorName,
            @Value("${app.privacy.contact-email:CHANGE_ME}") String contactEmail) {
        this.operatorName = operatorName;
        this.contactEmail = contactEmail;
    }

    @GetMapping("/privacy")
    public String privacy(Model model) {
        model.addAttribute("operatorName", operatorName);
        model.addAttribute("contactEmail", contactEmail);
        return "privacy";
    }
}
