package com.deveopsj.common.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.common.service.DataInputService;
import com.deveopsj.spending.dto.SpendingSaveRequest;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import com.deveopsj.member.entity.Member;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataInputController {

    private final DataInputService dataInputService;

    @PostMapping("/spending")
    public ResponseEntity<String> saveSpending(@Valid @RequestBody SpendingSaveRequest request, Member member) {
        dataInputService.saveSpendingWithAi(request, member);
        return ResponseEntity.ok("성공적으로 저장되었습니다.");
    }
}
