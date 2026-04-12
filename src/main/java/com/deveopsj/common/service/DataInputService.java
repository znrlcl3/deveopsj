package com.deveopsj.common.service;

import java.time.LocalDate;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.member.repository.MemberRepository;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataInputService {

    private final DailySpendingRepository dailySpendingRepository;
    private final MemberRepository memberRepository;
    private final AiService aiService;

    @Transactional
    public void saveSpendingWithAi(Map<String, Object> params) {
        String memo = params.get("memo").toString();
        String category = (params.get("category") != null) ? params.get("category").toString() : "";
        Long amount = Long.parseLong(params.get("amount").toString());
        LocalDate date = LocalDate.parse(params.get("date").toString());

        // 1. 카테고리가 비어있으면 AI에게 추측 요청
        if (category.isEmpty() || "NONE".equals(category)) {
            category = predictCategory(memo);
        }

        // 2. 엔티티 생성 및 저장
        DailySpending spending = DailySpending.builder()
                .member(memberRepository.findById(1L).orElseThrow()) // 1번 멤버 고정
                .amount(amount)
                .categoryCode(category)
                .spendingDate(date)
                .memo(memo)
                .build();

        dailySpendingRepository.save(spending);
    }

    private String predictCategory(String memo) {
        String prompt = String.format(
            "지출 메모 '%s'를 보고 다음 중 가장 적절한 카테고리 코드 하나만 답변해줘: " +
            "[FOOD, CAFE, TRANS, MART, ETC]. 설명 없이 코드만 딱 보내줘.", memo
        );
        
        // AiService의 기존 메서드를 활용하거나 단순 호출용 메서드 추가
        String aiResponse = aiService.getWealthFeedbackSimple(prompt); 
        
        // AI가 혹시 다른 말을 섞을 경우를 대비해 필터링 (예: "답변: FOOD" -> "FOOD")
        return aiResponse.toUpperCase().replaceAll("[^A-Z]", "");
    }
}