package com.deveopsj.ai.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.ai.dto.SpendingAnalysisRequest;
import com.deveopsj.ai.dto.SpendingAnalysisType;
import com.deveopsj.ai.service.SpendingAnalysisService;
import com.deveopsj.member.entity.Member;

@ExtendWith(MockitoExtension.class)
class AiRestControllerTest {

    @Mock
    private SpendingAnalysisService spendingAnalysisService;

    @InjectMocks
    private AiRestController controller;

    @Test
    void 로그인사용자의_선택월과_유형으로_지출을_분석한다() {
        Member member = new Member();
        member.setMemberId(7L);
        SpendingAnalysisRequest request = new SpendingAnalysisRequest();
        request.setMonth(YearMonth.of(2026, 7));
        request.setAnalysisType(SpendingAnalysisType.SAVING_OPPORTUNITIES);
        when(spendingAnalysisService.analyze(request, member)).thenReturn("분석 결과");

        Map<String, String> response = controller.analyzeSpending(request, member);

        assertThat(response).containsEntry("feedback", "분석 결과");
        verify(spendingAnalysisService).analyze(request, member);
    }
}
