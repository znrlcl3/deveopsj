package com.deveopsj.income.service;

import java.time.YearMonth;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.deveopsj.income.dto.IncomeSaveRequest;
import com.deveopsj.income.dto.IncomeUpdateRequest;
import com.deveopsj.income.entity.Income;
import com.deveopsj.income.repository.IncomeRepository;
import com.deveopsj.member.entity.Member;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class IncomeService {

    private final IncomeRepository incomeRepository;

    public void save(IncomeSaveRequest request, Member member) {
        Income income = new Income();
        apply(income, request);
        income.setMember(member);
        incomeRepository.save(income);
    }

    @Transactional(readOnly = true)
    public List<Income> getIncomesByMemberAndMonth(Member member, YearMonth month) {
        return incomeRepository
                .findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
                        member.getMemberId(), month.atDay(1), month.atEndOfMonth());
    }

    @Transactional(readOnly = true)
    public List<Income> getIncomesByMemberAndPeriod(Member member, LocalDate start, LocalDate end) {
        return incomeRepository.findByMemberMemberIdAndIncomeDateBetweenOrderByIncomeDateDescIdDesc(
                member.getMemberId(), start, end);
    }

    public void update(IncomeUpdateRequest request, Member member) {
        Income income = incomeRepository
                .findByIdAndMemberMemberId(request.getId(), member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "수정할 수 있는 수입 내역이 없습니다."));
        apply(income, request);
    }

    public void deleteById(Long id, Member member) {
        Income income = incomeRepository.findByIdAndMemberMemberId(id, member.getMemberId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "삭제할 수 있는 수입 내역이 없습니다."));
        incomeRepository.delete(income);
    }

    private void apply(Income income, IncomeSaveRequest request) {
        income.setIncomeDate(request.getIncomeDate());
        income.setIncomeType(request.getIncomeType());
        income.setAmount(request.getAmount());
        income.setMemo(normalizeMemo(request.getMemo()));
    }

    private String normalizeMemo(String memo) {
        return memo == null || memo.isBlank() ? null : memo.trim();
    }
}
