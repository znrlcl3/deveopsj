package com.deveopsj.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.ai.dto.SpendingAnalysisRequest;
import com.deveopsj.ai.dto.SpendingAnalysisType;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.service.SpendingService;

@ExtendWith(MockitoExtension.class)
class SpendingAnalysisServiceTest {

    @Mock
    private SpendingService spendingService;

    @Mock
    private MasterCodeService masterCodeService;

    @Mock
    private AiService aiService;

    @InjectMocks
    private SpendingAnalysisService spendingAnalysisService;

    @Test
    void 로그인사용자의_월간지출을_한글카테고리로_요약해_AI에_전달한다() {
        Member member = member();
        SpendingAnalysisRequest request = request(SpendingAnalysisType.SAVING_OPPORTUNITIES);
        DailySpending spending = DailySpending.builder()
                .spendingDate(LocalDate.of(2026, 7, 3))
                .categoryCode("FOOD")
                .amount(25_000L)
                .memo("배달 저녁")
                .build();
        when(spendingService.getSpendings(
                member, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(spending));
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build()));
        when(aiService.getSpendingAnalysis(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("절약 분석");

        String result = spendingAnalysisService.analyze(request, member);

        assertThat(result).isEqualTo("절약 분석");
        verify(aiService).getSpendingAnalysis(argThat(prompt ->
                prompt.contains("분석 월: 2026-07")
                        && prompt.contains("식비: 25000원")
                        && prompt.contains("배달 저녁")
                        && !prompt.contains("memberId")));
    }

    @Test
    void 지난달비교는_선택월과_이전월을_같은사용자기준으로_조회한다() {
        Member member = member();
        SpendingAnalysisRequest request = request(SpendingAnalysisType.MONTHLY_COMPARISON);
        DailySpending current = DailySpending.builder()
                .categoryCode("FOOD").amount(10_000L).memo("점심").build();
        when(spendingService.getSpendings(
                member, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of(current));
        when(spendingService.getSpendings(
                member, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of());
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(List.of());
        when(aiService.getSpendingAnalysis(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("비교 분석");

        spendingAnalysisService.analyze(request, member);

        verify(spendingService).getSpendings(
                member, LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));
        verify(aiService).getSpendingAnalysis(argThat(prompt ->
                prompt.contains("<previous-month>")
                        && prompt.contains("분석 월: 2026-06")));
    }

    @Test
    void 선택월에_지출이없으면_AI를_호출하지_않는다() {
        Member member = member();
        SpendingAnalysisRequest request = request(SpendingAnalysisType.CATEGORY_REVIEW);
        when(spendingService.getSpendings(
                member, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)))
                .thenReturn(List.of());

        String result = spendingAnalysisService.analyze(request, member);

        assertThat(result).contains("지출 내역이 없어");
        verify(aiService, never()).getSpendingAnalysis(
                org.mockito.ArgumentMatchers.anyString());
    }

    private SpendingAnalysisRequest request(SpendingAnalysisType type) {
        SpendingAnalysisRequest request = new SpendingAnalysisRequest();
        request.setMonth(YearMonth.of(2026, 7));
        request.setAnalysisType(type);
        return request;
    }

    private Member member() {
        Member member = new Member();
        member.setMemberId(7L);
        return member;
    }
}
