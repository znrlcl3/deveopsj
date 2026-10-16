package com.deveopsj.spending.dto;
import java.io.Serializable;
import java.time.LocalDateTime;
public record SpendingDuplicateMatch(Long id, String categoryCode, LocalDateTime createDate)
        implements Serializable {}
