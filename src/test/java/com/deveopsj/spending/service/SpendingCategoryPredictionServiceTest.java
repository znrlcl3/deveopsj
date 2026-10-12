package com.deveopsj.spending.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;

import org.junit.jupiter.api.Test;

import com.deveopsj.ai.service.AiService;
import com.deveopsj.common.dto.MasterCodeDto;
import com.deveopsj.common.service.MasterCodeService;

class SpendingCategoryPredictionServiceTest {

    @Test
    void 빈_카테고리는_AI가_현재_마스터코드에서_분류한다() {
        AiService ai = mock(AiService.class);
        MasterCodeService codes = mock(MasterCodeService.class);
        when(codes.getActiveCodesByGroup("SPENDING_CAT")).thenReturn(categories());
        when(ai.getWealthFeedbackSimple(contains("SHOPPING(쇼핑)"))).thenReturn("SHOPPING");
        SpendingCategoryPredictionService service = new SpendingCategoryPredictionService(ai, codes);

        assertThat(service.resolve("", "온라인 상점")).isEqualTo("SHOPPING");
    }

    @Test
    void AI가_잘못된_응답을_주면_기타로_fallback한다() {
        AiService ai = mock(AiService.class);
        SpendingCategoryPredictionService service = new SpendingCategoryPredictionService(
                ai, mock(MasterCodeService.class));
        when(ai.getWealthFeedbackSimple(org.mockito.ArgumentMatchers.anyString())).thenReturn("UNKNOWN");

        assertThat(service.predictByMerchant(Set.of("상점"), categories())).containsEntry("상점", "ETC");
    }

    @Test
    void 같은_가맹점은_한번만_AI에_요청한다() {
        AiService ai = mock(AiService.class);
        SpendingCategoryPredictionService service = new SpendingCategoryPredictionService(
                ai, mock(MasterCodeService.class));
        when(ai.getWealthFeedbackSimple(org.mockito.ArgumentMatchers.anyString())).thenReturn("1=FOOD");

        service.predictByMerchant(Set.of("같은 가맹점"), categories());

        verify(ai, times(1)).getWealthFeedbackSimple(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 여러_가맹점을_한번의_AI호출로_분류한다() {
        AiService ai = mock(AiService.class);
        SpendingCategoryPredictionService service = new SpendingCategoryPredictionService(
                ai, mock(MasterCodeService.class));
        when(ai.getWealthFeedbackSimple(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("1=FOOD\n2=SHOPPING\n3=MEDICAL");
        Set<String> merchants = new LinkedHashSet<>(List.of("식당", "쇼핑몰", "알 수 없는 곳"));

        var result = service.predictByMerchant(merchants, categories());

        assertThat(result).containsEntry("식당", "FOOD")
                .containsEntry("쇼핑몰", "SHOPPING")
                .containsEntry("알 수 없는 곳", "ETC");
        verify(ai, times(1)).getWealthFeedbackSimple(org.mockito.ArgumentMatchers.argThat(prompt ->
                prompt.contains("1. 식당") && prompt.contains("2. 쇼핑몰")
                        && !prompt.contains("금액") && !prompt.contains("카드번호")));
    }

    private List<MasterCodeDto> categories() {
        return List.of(
                MasterCodeDto.builder().codeId("FOOD").codeName("식비").build(),
                MasterCodeDto.builder().codeId("SHOPPING").codeName("쇼핑").build(),
                MasterCodeDto.builder().codeId("ETC").codeName("기타").build());
    }
}
