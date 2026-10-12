package com.deveopsj.spending.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SpendingCategoryPredictionService {

    private static final Pattern BATCH_RESPONSE_LINE = Pattern.compile(
            "(?m)^\\s*(\\d+)\\s*=\\s*([A-Za-z_]+)\\s*$");

    private final AiService aiService;
    private final MasterCodeService masterCodeService;

    public String resolve(String requestedCategory, String merchantName) {
        List<MasterCodeDto> categories = activeCategories();
        Set<String> allowed = allowedCodes(categories);
        if (requestedCategory != null && !requestedCategory.isBlank()
                && !"NONE".equals(requestedCategory)) {
            if (!allowed.contains(requestedCategory)) {
                throw new IllegalArgumentException("사용할 수 없는 지출 카테고리입니다.");
            }
            return requestedCategory;
        }
        return predict(merchantName, categories, allowed);
    }

    public Map<String, String> predictByMerchant(Set<String> merchantNames,
            List<MasterCodeDto> categories) {
        if (categories.isEmpty()) throw new IllegalStateException("사용 가능한 지출 카테고리가 없습니다.");
        if (merchantNames.isEmpty()) return Map.of();
        Set<String> allowed = allowedCodes(categories);
        List<String> merchants = new ArrayList<>(merchantNames);
        String options = categoryOptions(categories);
        StringBuilder prompt = new StringBuilder()
                .append("다음 지출 가맹점들을 카테고리로 분류해줘.\n")
                .append("사용 가능한 카테고리: [").append(options).append("]\n")
                .append("각 줄은 반드시 번호=카테고리코드 형식으로만 답변하고 설명은 쓰지 마.\n")
                .append("가맹점:\n");
        for (int i = 0; i < merchants.size(); i++) {
            prompt.append(i + 1).append(". ")
                    .append(merchants.get(i).replaceAll("\\s+", " ").trim()).append('\n');
        }

        String response = aiService.getWealthFeedbackSimple(prompt.toString());
        Map<Integer, String> responseByNumber = new LinkedHashMap<>();
        Matcher matcher = BATCH_RESPONSE_LINE.matcher(response);
        while (matcher.find()) {
            int number = Integer.parseInt(matcher.group(1));
            String code = matcher.group(2).toUpperCase(Locale.ROOT);
            if (number >= 1 && number <= merchants.size() && allowed.contains(code)) {
                responseByNumber.putIfAbsent(number, code);
            }
        }

        Map<String, String> result = new LinkedHashMap<>();
        String fallback = fallback(categories, allowed);
        for (int i = 0; i < merchants.size(); i++) {
            result.put(merchants.get(i), responseByNumber.getOrDefault(i + 1, fallback));
        }
        return result;
    }

    public List<MasterCodeDto> activeCategories() {
        List<MasterCodeDto> categories = masterCodeService.getActiveCodesByGroup("SPENDING_CAT");
        if (categories.isEmpty()) throw new IllegalStateException("사용 가능한 지출 카테고리가 없습니다.");
        return categories;
    }

    private String predict(String merchantName, List<MasterCodeDto> categories, Set<String> allowed) {
        String options = categoryOptions(categories);
        String prompt = String.format(
                "지출 가맹점 '%s'를 보고 다음 중 가장 적절한 카테고리 코드 하나만 답변해줘: "
                        + "[%s]. 설명 없이 코드만 답변해줘.", merchantName, options);
        String response = aiService.getWealthFeedbackSimple(prompt);
        String normalized = response.toUpperCase(Locale.ROOT).replaceAll("[^A-Z_]", "");
        if (allowed.contains(normalized)) return normalized;
        return fallback(categories, allowed);
    }

    private String categoryOptions(List<MasterCodeDto> categories) {
        return categories.stream()
                .map(category -> category.getCodeId() + "(" + category.getCodeName() + ")")
                .collect(Collectors.joining(", "));
    }

    private String fallback(List<MasterCodeDto> categories, Set<String> allowed) {
        return allowed.contains("ETC") ? "ETC" : categories.get(0).getCodeId();
    }

    private Set<String> allowedCodes(List<MasterCodeDto> categories) {
        return categories.stream().map(MasterCodeDto::getCodeId).collect(Collectors.toSet());
    }
}
