package com.deveopsj.common.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataInputService {

    private final DailySpendingRepository dailySpendingRepository;
    private final AiService aiService;
    private final MasterCodeService masterCodeService;

    @Transactional
    public void saveSpendingWithAi(SpendingSaveRequest request, com.deveopsj.member.entity.Member member) {
        String memo = request.getMemo().trim();
        String category = request.getCategory() == null ? "" : request.getCategory();

        List<MasterCodeDto> spendingCategories = masterCodeService.getActiveCodesByGroup("SPENDING_CAT");
        if (spendingCategories.isEmpty()) {
            throw new IllegalStateException("사용 가능한 지출 카테고리가 없습니다.");
        }

        Set<String> allowedCategoryCodes = spendingCategories.stream()
                .map(MasterCodeDto::getCodeId)
                .collect(Collectors.toSet());

        // 1. 카테고리가 비어있으면 현재 마스터 코드를 기준으로 AI에게 추측 요청
        if (category.isEmpty() || "NONE".equals(category)) {
            category = predictCategory(memo, spendingCategories, allowedCategoryCodes);
        } else if (!allowedCategoryCodes.contains(category)) {
            throw new IllegalArgumentException("사용할 수 없는 지출 카테고리입니다.");
        }

        // 2. 엔티티 생성 및 저장
        DailySpending spending = DailySpending.builder()
                .member(member)
                .amount(request.getAmount())
                .categoryCode(category)
                .spendingDate(request.getDate())
                .memo(memo)
                .build();

        dailySpendingRepository.save(spending);
    }

    private String predictCategory(String memo, List<MasterCodeDto> categories, Set<String> allowedCategoryCodes) {
        String categoryOptions = categories.stream()
                .map(category -> category.getCodeId() + "(" + category.getCodeName() + ")")
                .collect(Collectors.joining(", "));

        String prompt = String.format(
            "지출 메모 '%s'를 보고 다음 중 가장 적절한 카테고리 코드 하나만 답변해줘: " +
            "[%s]. 설명 없이 코드만 답변해줘.", memo, categoryOptions
        );

        String aiResponse = aiService.getWealthFeedbackSimple(prompt);
        String normalizedCode = aiResponse.toUpperCase(Locale.ROOT).replaceAll("[^A-Z_]", "");

        if (allowedCategoryCodes.contains(normalizedCode)) {
            return normalizedCode;
        }
        return allowedCategoryCodes.contains("ETC") ? "ETC" : categories.get(0).getCodeId();
    }
}
