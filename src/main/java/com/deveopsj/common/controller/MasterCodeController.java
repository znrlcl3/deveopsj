package com.deveopsj.common.controller; 
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/codes")
@RequiredArgsConstructor
public class MasterCodeController {

    private final MasterCodeService masterCodeService;

    /**
     * 그룹 코드를 받아 사용 중인(use_yn='Y') 마스터 코드 목록 반환
     */
    @GetMapping("/{groupCode}")
    public ResponseEntity<List<MasterCodeDto>> getCodes(@PathVariable String groupCode) {
        List<MasterCodeDto> codes = masterCodeService.getActiveCodesByGroup(groupCode);
        return ResponseEntity.ok(codes);
    }
}