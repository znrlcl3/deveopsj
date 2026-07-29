package com.deveopsj.market.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.ExtendedModelMap;

import com.deveopsj.market.dto.KrxStockItem;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.dto.KrxStockSnapshot;
import com.deveopsj.market.service.KrxStockService;
import com.deveopsj.market.service.InvestmentAssetSyncService;
import com.deveopsj.market.service.NasdaqDirectoryService;

@ExtendWith(MockitoExtension.class)
class KrxTestControllerTest {

    @Mock
    private KrxStockService krxStockService;

    @Mock
    private NasdaqDirectoryService nasdaqDirectoryService;

    @Mock
    private InvestmentAssetSyncService investmentAssetSyncService;

    @InjectMocks
    private KrxTestController controller;

    @Test
    void 테스트화면에_실제기준일과_종목수를_전달한다() {
        LocalDate requestedDate = LocalDate.of(2026, 7, 28);
        LocalDate baseDate = LocalDate.of(2026, 7, 27);
        KrxStockItem stock = new KrxStockItem(
                "KR7005930003", "005930", "삼성전자보통주", "삼성전자",
                "Samsung Electronics", "19750611", "KOSPI", "주권", "보통주", "5969782550");
        when(krxStockService.fetchLatest(requestedDate, KrxMarket.KOSDAQ))
                .thenReturn(new KrxStockSnapshot(baseDate, List.of(stock)));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.testPage(requestedDate, KrxMarket.KOSDAQ, model);

        assertThat(view).isEqualTo("krx/test");
        assertThat(model.get("baseDate")).isEqualTo(baseDate);
        assertThat(model.get("totalCount")).isEqualTo(1);
        assertThat(model.get("stocks")).isEqualTo(List.of(stock));
        assertThat(model.get("selectedMarket")).isEqualTo(KrxMarket.KOSDAQ);
    }

    @Test
    void 미국주식은_Nasdaq공식파일을_조회한다() {
        KrxStockItem stock = new KrxStockItem(
                null, "AAPL", "Apple Inc.", "Apple Inc.",
                null, null, "NASDAQ", "STOCK", "STOCK", null);
        when(nasdaqDirectoryService.fetchAll()).thenReturn(List.of(stock));
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.testPage(
                LocalDate.of(2026, 7, 28), KrxMarket.US, model);

        assertThat(view).isEqualTo("krx/test");
        assertThat(model.get("stocks")).isEqualTo(List.of(stock));
        assertThat(model.get("totalCount")).isEqualTo(1);
    }
}
