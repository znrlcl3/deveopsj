package com.deveopsj.spending.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.repository.DailySpendingRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class SpendingService {

    private final DailySpendingRepository dailySpendingRepository;

    public void deleteById(Long id, Member member) {
        DailySpending spending = dailySpendingRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException("삭제할 수 있는 지출 내역이 없습니다."));
        dailySpendingRepository.delete(spending);
    }
}
