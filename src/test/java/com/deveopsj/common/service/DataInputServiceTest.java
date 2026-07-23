package com.deveopsj.common.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.member.entity.Member;
import com.deveopsj.spending.entity.DailySpending;
import com.deveopsj.spending.dto.SpendingSaveRequest;
import com.deveopsj.spending.repository.DailySpendingRepository;

@ExtendWith(MockitoExtension.class)
class DataInputServiceTest {

    @Mock
    private DailySpendingRepository dailySpendingRepository;

    @Mock
    private AiService aiService;

    @Mock
    private MasterCodeService masterCodeService;

    @InjectMocks
    private DataInputService dataInputService;

    @Test
    void AI는_현재_마스터코드에서만_카테고리를_선택한다() {
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(categories());
        when(aiService.getWealthFeedbackSimple(contains("SHOPPING(쇼핑)"))).thenReturn("SHOPPING");

        dataInputService.saveSpendingWithAi(params("NONE"), member());

        ArgumentCaptor<DailySpending> captor = ArgumentCaptor.forClass(DailySpending.class);
        verify(dailySpendingRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCategoryCode()).isEqualTo("SHOPPING");
    }

    @Test
    void AI가_잘못된_코드를_응답하면_기타로_저장한다() {
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(categories());
        when(aiService.getWealthFeedbackSimple(org.mockito.ArgumentMatchers.anyString())).thenReturn("CAFE");

        dataInputService.saveSpendingWithAi(params("NONE"), member());

        ArgumentCaptor<DailySpending> captor = ArgumentCaptor.forClass(DailySpending.class);
        verify(dailySpendingRepository).save(captor.capture());
        org.assertj.core.api.Assertions.assertThat(captor.getValue().getCategoryCode()).isEqualTo("ETC");
    }

    @Test
    void 마스터코드에_없는_수동_카테고리는_저장하지_않는다() {
        when(masterCodeService.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(categories());

        assertThatThrownBy(() -> dataInputService.saveSpendingWithAi(params("CAFE"), member()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(dailySpendingRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    private List<MasterCodeDto> categories() {
        return List.of(
                MasterCodeDto.builder().groupId("SPENDING_CAT").codeId("FOOD").codeName("식비").build(),
                MasterCodeDto.builder().groupId("SPENDING_CAT").codeId("SHOPPING").codeName("쇼핑").build(),
                MasterCodeDto.builder().groupId("SPENDING_CAT").codeId("ETC").codeName("기타").build());
    }

    private SpendingSaveRequest params(String category) {
        SpendingSaveRequest request = new SpendingSaveRequest();
        request.setMemo("온라인 쇼핑");
        request.setCategory(category);
        request.setAmount(10_000L);
        request.setDate(LocalDate.of(2026, 7, 22));
        return request;
    }

    private Member member() {
        Member member = new Member();
        member.setMemberId(7L);
        return member;
    }
}
