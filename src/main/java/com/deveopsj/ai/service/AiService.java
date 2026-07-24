package com.deveopsj.ai.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.ai.config.GeminiProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final GeminiProperties geminiProperties;

    public String getSpendingAnalysis(String promptText) {
        return callGemini(promptText, "AI 지출 분석을 완료하지 못했습니다.", "지출 분석");
    }

    public String getWealthFeedbackSimple(String promptText) {
        return callGemini(promptText, "ETC", "카테고리 분류");
    }

    private String callGemini(String promptText, String fallback, String operation) {
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(Map.of("text", promptText)))));

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            log.info("Gemini API 호출 시작 (모델: {}, 작업: {})",
                    geminiProperties.getModel(), operation);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    geminiProperties.generateContentUrl(), entity, Map.class);

            String text = extractText(response.getBody());
            if (text != null && !text.isBlank()) {
                log.info("Gemini API 응답 성공 (모델: {}, 작업: {})",
                        geminiProperties.getModel(), operation);
                return text;
            }
            log.warn("Gemini API 빈 응답 (모델: {}, 작업: {})",
                    geminiProperties.getModel(), operation);
            return fallback;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("Gemini API 호출 실패 (모델: {}, 작업: {}, 상태: {})",
                    geminiProperties.getModel(), operation, e.getStatusCode());
            return fallback;
        } catch (Exception e) {
            log.error("Gemini API 호출 중 오류 발생 (모델: {}, 작업: {})",
                    geminiProperties.getModel(), operation, e);
            return fallback;
        }
    }

    private String extractText(Map<?, ?> responseBody) {
        if (responseBody == null || !(responseBody.get("candidates") instanceof List<?> candidates)
                || candidates.isEmpty() || !(candidates.get(0) instanceof Map<?, ?> candidate)
                || !(candidate.get("content") instanceof Map<?, ?> content)
                || !(content.get("parts") instanceof List<?> parts)
                || parts.isEmpty() || !(parts.get(0) instanceof Map<?, ?> part)
                || !(part.get("text") instanceof String text)) {
            return null;
        }
        return text;
    }
}
