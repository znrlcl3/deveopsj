package com.deveopsj.common.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.common.service.DataInputService;

import lombok.RequiredArgsConstructor;

import com.deveopsj.member.entity.Member;

@RestController
@RequestMapping("/api/data")
@RequiredArgsConstructor
public class DataInputController {

    private final DataInputService dataInputService;

    @PostMapping("/spending")
    public ResponseEntity<String> saveSpending(@RequestBody Map<String, Object> params, Member member) {
        dataInputService.saveSpendingWithAi(params, member);
        return ResponseEntity.ok("성공적으로 저장되었습니다.");
    }
}