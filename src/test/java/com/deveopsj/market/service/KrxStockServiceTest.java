package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.market.config.KrxApiProperties;
import com.deveopsj.market.dto.KrxEtfItem;
import com.deveopsj.market.dto.KrxEtfResponse;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.dto.KrxStockItem;
import com.deveopsj.market.dto.KrxStockResponse;
import com.deveopsj.market.dto.KrxStockSnapshot;

@ExtendWith(MockitoExtension.class)
class KrxStockServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private KrxStockService krxStockService;

    @BeforeEach
    void setUp() {
        KrxApiProperties properties = new KrxApiProperties();
        properties.setBaseUrl("https://krx.test/svc/apis");
        properties.setAuthKey("test-key");
        krxStockService = new KrxStockService(restTemplate, properties);
    }

    @Test
    void 요청일에_데이터가없으면_직전영업일을_조회한다() {
        KrxStockItem samsung = new KrxStockItem(
                "KR7005930003", "005930", "삼성전자보통주", "삼성전자",
                "Samsung Electronics", "19750611", "KOSPI", "주권", "보통주", "5969782550");
        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(),
                eq(KrxStockResponse.class)))
                .thenReturn(ResponseEntity.ok(new KrxStockResponse(List.of())))
                .thenReturn(ResponseEntity.ok(new KrxStockResponse(List.of(samsung))));

        KrxStockSnapshot result = krxStockService.fetchLatest(
                LocalDate.of(2026, 7, 28), KrxMarket.KOSPI);

        assertThat(result.baseDate()).isEqualTo(LocalDate.of(2026, 7, 27));
        assertThat(result.stocks()).containsExactly(samsung);
    }

    @Test
    void 미래날짜는_KRX를_호출하지않고_거부한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> krxStockService.fetchLatest(
                        LocalDate.now().plusDays(1), KrxMarket.KOSPI))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미래");
    }

    @Test
    void ETF응답을_공통종목형식으로_변환한다() {
        when(restTemplate.exchange(any(java.net.URI.class), eq(HttpMethod.GET), any(),
                eq(KrxEtfResponse.class)))
                .thenReturn(ResponseEntity.ok(new KrxEtfResponse(List.of(
                        new KrxEtfItem("451060", "1Q 200액티브", "12300000")))));

        KrxStockSnapshot result = krxStockService.fetchLatest(
                LocalDate.of(2026, 7, 27), KrxMarket.ETF);

        assertThat(result.stocks()).singleElement().satisfies(stock -> {
            assertThat(stock.shortCode()).isEqualTo("451060");
            assertThat(stock.abbreviatedName()).isEqualTo("1Q 200액티브");
            assertThat(stock.market()).isEqualTo("ETF");
            assertThat(stock.securityGroup()).isEqualTo("ETF");
        });
    }
}
