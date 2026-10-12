package com.deveopsj.common.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.repository.DailySpendingRepository;
import com.deveopsj.spending.service.SpendingCategoryPredictionService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DataInputService {

    private final DailySpendingRepository dailySpendingRepository;
    private final SpendingCategoryPredictionService categoryPredictionService;

    @Transactional
    public void saveSpendingWithAi(SpendingSaveRequest request, com.deveopsj.member.entity.Member member) {
        String memo = request.getMemo().trim();
        String category = categoryPredictionService.resolve(request.getCategory(), memo);

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

}
