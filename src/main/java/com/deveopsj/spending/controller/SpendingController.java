package com.deveopsj.spending.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.deveopsj.common.service.DataInputService; // (추후 SpendingService로 변경 추천)

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/spending")
@RequiredArgsConstructor
public class SpendingController {

    private final DataInputService dataInputService; 

    @GetMapping("/form")
    public String spendingForm() {
        return "spending/form"; 
    }

    @PostMapping("/api/save")
    @ResponseBody
    public ResponseEntity<String> saveSpending(@RequestBody Map<String, Object> params) {
        dataInputService.saveSpendingWithAi(params);
        return ResponseEntity.ok("성공적으로 저장되었습니다.");
    }
}