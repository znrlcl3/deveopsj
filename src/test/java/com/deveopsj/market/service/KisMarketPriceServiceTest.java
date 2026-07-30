package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.market.config.KisApiProperties;
import com.deveopsj.market.dto.MarketPriceQuote;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KisMarketPriceServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KisAccessTokenService accessTokenService;

    @Mock
    private InvestmentAssetRepository investmentAssetRepository;

    private KisMarketPriceService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        KisApiProperties properties = new KisApiProperties();
        properties.setBaseUrl("https://example.test");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        service = new KisMarketPriceService(
                restTemplate, properties, accessTokenService, investmentAssetRepository);
        when(accessTokenService.getAccessToken()).thenReturn("access-token");
    }

    @Test
    void 국내종목_현재가를_조회한다() throws Exception {
        InvestmentAsset asset = asset(1L, "005930", "삼성전자", "KOSPI", "KRW");
        when(investmentAssetRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(asset));
        JsonNode response = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output": {
                    "stck_prpr": "73500",
                    "stck_sdpr": "72000",
                    "prdy_vrss": "1500",
                    "prdy_ctrt": "2.08"
                  }
                }
                """);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(response));

        MarketPriceQuote quote = service.getQuote(1L);

        assertThat(quote.currentPrice()).isEqualByComparingTo(new BigDecimal("73500"));
        assertThat(quote.previousClose()).isEqualByComparingTo(new BigDecimal("72000"));
        assertThat(quote.currency()).isEqualTo("KRW");
        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.contains("FID_INPUT_ISCD=005930"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 나스닥종목_현재가를_조회한다() throws Exception {
        InvestmentAsset asset = asset(2L, "QQQM", "Invesco NASDAQ 100 ETF", "NASDAQ", "USD");
        when(investmentAssetRepository.findByIdAndActiveTrue(2L)).thenReturn(Optional.of(asset));
        JsonNode response = objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output": {
                    "last": "245.12",
                    "base": "242.00",
                    "diff": "-",
                    "rate": " 1.29% "
                  }
                }
                """);
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(response));

        MarketPriceQuote quote = service.getQuote(2L);

        assertThat(quote.currentPrice()).isEqualByComparingTo(new BigDecimal("245.12"));
        assertThat(quote.changeRate()).isEqualByComparingTo(new BigDecimal("1.29"));
        assertThat(quote.change()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(quote.currency()).isEqualTo("USD");
        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.contains("EXCD=NAS"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 캐시유효시간에는_KIS를_다시_호출하지_않는다() throws Exception {
        InvestmentAsset asset = asset(1L, "005930", "삼성전자", "KOSPI", "KRW");
        when(investmentAssetRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(asset));
        JsonNode response = domesticResponse();
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(response));

        MarketPriceQuote first = service.getQuote(1L);
        MarketPriceQuote second = service.getQuote(1L);

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.stale()).isFalse();
        verify(restTemplate, times(1)).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 갱신실패시_최근_조회가격을_사용한다() throws Exception {
        KisApiProperties properties = new KisApiProperties();
        properties.setBaseUrl("https://example.test");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        properties.setQuoteCacheTtl(Duration.ZERO);
        properties.setStaleQuoteTtl(Duration.ofHours(24));
        service = new KisMarketPriceService(
                restTemplate, properties, accessTokenService, investmentAssetRepository);

        InvestmentAsset asset = asset(1L, "005930", "삼성전자", "KOSPI", "KRW");
        when(investmentAssetRepository.findByIdAndActiveTrue(1L)).thenReturn(Optional.of(asset));
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(domesticResponse()))
                .thenThrow(new ResourceAccessException("timeout"));

        service.getQuote(1L);
        MarketPriceQuote fallback = service.getQuote(1L);

        assertThat(fallback.currentPrice()).isEqualByComparingTo("73500");
        assertThat(fallback.cached()).isTrue();
        assertThat(fallback.stale()).isTrue();
        verify(restTemplate, times(2)).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    private JsonNode domesticResponse() throws Exception {
        return objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output": {
                    "stck_prpr": "73500",
                    "stck_sdpr": "72000",
                    "prdy_vrss": "1500",
                    "prdy_ctrt": "2.08"
                  }
                }
                """);
    }

    private InvestmentAsset asset(Long id, String symbol, String name, String market, String currency) {
        InvestmentAsset asset = new InvestmentAsset();
        asset.setId(id);
        asset.setSymbol(symbol);
        asset.setAssetName(name);
        asset.setMarket(market);
        asset.setAssetClass("ETF");
        asset.setCurrency(currency);
        asset.setActive(true);
        return asset;
    }
}
