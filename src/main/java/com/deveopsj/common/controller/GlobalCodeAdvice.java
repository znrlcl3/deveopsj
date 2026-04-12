package com.deveopsj.common.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;

import lombok.RequiredArgsConstructor;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalCodeAdvice {

    private final MasterCodeService masterCodeService;

    @ModelAttribute("codeMap")
    public Map<String, List<MasterCodeDto>> globalCodes() {
        return masterCodeService.getAllActiveCodesGrouped();
    }
}