package com.deveopsj.ai.service;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.dashboard.dto.DashboardSummary;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiService {

    // application.properties에 설정된 API 키 주입
    @Value("${spring.ai.gemini.api-key}")
    private String apiKey;

 // 리스트의 첫 번째 모델인 gemini-2.5-flash를 사용합니다.
    private final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=";

    public String getWealthFeedback(DashboardSummary summary) {
        RestTemplate restTemplate = new RestTemplate();
        
        // 1. 프롬프트 구성 (성진 대리님 맞춤형 페르소나 적용)
        String promptText = String.format(
            "너는 깐깐하지만 유능한 자산 관리 전문가야. 아래 데이터를 보고 '이성진 대리'에게 조언해줘.\n" +
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
            System.out.println(">>> Gemini API 호출 시작 (모델: gemini-1.5-flash)");
            ResponseEntity<Map> response = restTemplate.postForEntity(GEMINI_URL + apiKey, entity, Map.class);
            
            // 3. 응답 데이터에서 텍스트 추출 (JSON 트리 구조 탐색)
            if (response.getBody() != null && response.getBody().containsKey("candidates")) {
                List candidates = (List) response.getBody().get("candidates");
                if (!candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map content = (Map) firstCandidate.get("content");
                    List parts = (List) content.get("parts");
                    Map firstPart = (Map) parts.get(0);
                    
                    String feedback = (String) firstPart.get("text");
                    System.out.println(">>> AI 응답 성공: " + feedback);
                    return feedback;
                }
            }
            return "AI 비서가 응답을 생성하지 못했습니다.";

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // 401, 404 등 HTTP 에러 발생 시 로그 출력
            System.err.println("### API 호출 에러: " + e.getStatusCode());
            System.err.println("### 에러 원본: " + e.getResponseBodyAsString());
            return "AI 인증 또는 주소 에러가 발생했습니다. (404/401)";
        } catch (Exception e) {
            // 기타 예외 처리
            e.printStackTrace();
            return "AI 분석 중 오류가 발생했습니다: " + e.getMessage();
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

            // 2. API 호출 (v1 / gemini-2.5-flash)
            ResponseEntity<Map> response = restTemplate.postForEntity(GEMINI_URL + apiKey, entity, Map.class);
            
            // 3. JSON 결과 파싱 (안전하게 추출)
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
            System.err.println("### Simple AI 호출 에러: " + e.getMessage());
            return "ETC"; // 에러 발생 시 시스템 중단을 막기 위해 기본 카테고리(ETC) 리턴
        }
    }
}