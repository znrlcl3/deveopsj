package com.deveopsj.common.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.repository.MasterCodeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MasterCodeService {

    private final MasterCodeRepository masterCodeRepository;

    // 1. 단일 그룹 조회
    public List<MasterCodeDto> getActiveCodesByGroup(String groupId) {
        return masterCodeRepository.findByGroupIdAndDisableDateIsNullOrderBySortOrderAsc(groupId)
                .stream()
                .map(MasterCodeDto::fromEntity)
                .collect(Collectors.toList());
    }

    // 2. 전체 그룹핑 조회 (@ControllerAdvice 용)
    public Map<String, List<MasterCodeDto>> getAllActiveCodesGrouped() {
        return masterCodeRepository.findByDisableDateIsNullOrderBySortOrderAsc()
                .stream()
                .map(MasterCodeDto::fromEntity)
                // ✨ getGroupId 로 그룹핑
                .collect(Collectors.groupingBy(MasterCodeDto::getGroupId)); 
    }
}