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
import com.deveopsj.dashboard.dto.DashboardSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final GeminiProperties geminiProperties;
    public String getWealthFeedback(DashboardSummary summary) {
        RestTemplate restTemplate = new RestTemplate();
        
        // 1. 프롬프트 구성
        String promptText = String.format(
            "너는 깐깐하지만 유능한 자산 관리 전문가야. 아래 데이터를 보고 '이성진'에게 조언해줘.\n" +
            "- 현재 투자액: %d원 (월 목표 2,050,000원)\n" +
            "- 현재 지출액: %d원 (월 목표 550,000원)\n" +
            "- 투자 달성률: %.1f%%\n" +
            "칭찬은 짧게, 조언은 날카롭게 반말로 150자 이내로 답변해줘.",
            summary.getTotalInvestment(), 
            summary.getTotalSpending(), 
            summary.getInvestmentProgress()
        );

        // 2. 구글 Gemini API 규격에 맞춘 요청 바디 구성
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", promptText)))
            )
        );

        try {
            // 헤더 설정
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // API 호출
            log.info("Gemini API 호출 시작 (모델: {})", geminiProperties.getModel());
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    geminiProperties.generateContentUrl(), entity, Map.class);
            
            // 3. 응답 데이터에서 텍스트 추출 (JSON 트리 구조 탐색)
            if (response.getBody() != null && response.getBody().containsKey("candidates")) {
                List candidates = (List) response.getBody().get("candidates");
                if (!candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map content = (Map) firstCandidate.get("content");
                    List parts = (List) content.get("parts");
                    Map firstPart = (Map) parts.get(0);
                    
                    String feedback = (String) firstPart.get("text");
                    log.info("Gemini API 응답 성공 (모델: {})", geminiProperties.getModel());
                    return feedback;
                }
            }
            return "AI 비서가 응답을 생성하지 못했습니다.";

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.warn("Gemini API 호출 실패 (모델: {}, 상태: {})",
                    geminiProperties.getModel(), e.getStatusCode());
            return "AI 인증 또는 주소 에러가 발생했습니다. (404/401)";
        } catch (Exception e) {
            log.error("Gemini API 호출 중 오류 발생 (모델: {})",
                    geminiProperties.getModel(), e);
            return "AI 분석 중 오류가 발생했습니다.";
        }
    }
    
    /**
     * 단순 텍스트 프롬프트를 전달하여 AI 답변을 받아오는 범용 메서드
     * (카테고리 추측, 에러 분석 등에 활용)
     */
    public String getWealthFeedbackSimple(String promptText) {
        RestTemplate restTemplate = new RestTemplate();
        
        // 1. 요청 바디 구성
        Map<String, Object> requestBody = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(Map.of("text", promptText)))
            )
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // 2. API 호출
            log.info("Gemini API 호출 시작 (모델: {})", geminiProperties.getModel());
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    geminiProperties.generateContentUrl(), entity, Map.class);
            
            // 3. JSON 결과 파싱
            if (response.getBody() != null && response.getBody().containsKey("candidates")) {
                List candidates = (List) response.getBody().get("candidates");
                if (!candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map content = (Map) firstCandidate.get("content");
                    List parts = (List) content.get("parts");
                    
                    // AI 답변 텍스트 리턴
                    return (String) ((Map) parts.get(0)).get("text");
                }
            }
            return "ETC"; // 응답 구조가 이상할 경우 기본값 리턴

        } catch (Exception e) {
            log.warn("Gemini API 단순 호출 실패 (모델: {})", geminiProperties.getModel());
            return "ETC"; // 에러 발생 시 시스템 중단을 막기 위해 기본 카테고리(ETC) 리턴
        }
    }
}
