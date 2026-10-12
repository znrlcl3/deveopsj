package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

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

import com.deveopsj.market.config.KisApiProperties;
import com.deveopsj.market.dto.ExchangeRateQuote;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KisExchangeRateServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private KisAccessTokenService accessTokenService;

    private KisExchangeRateService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        service = new KisExchangeRateService(restTemplate, properties(), accessTokenService);
    }

    @Test
    void 원달러_환율을_조회한다() throws Exception {
        when(accessTokenService.getAccessToken()).thenReturn("access-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(exchangeRateResponse()));

        ExchangeRateQuote quote = service.getKrwRate("USD");

        assertThat(quote.krwRate()).isEqualByComparingTo("1468.50");
        assertThat(quote.currency()).isEqualTo("USD");
        assertThat(quote.cached()).isFalse();
        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.contains("FID_COND_MRKT_DIV_CODE=X"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
        verify(restTemplate).exchange(
                org.mockito.ArgumentMatchers.contains("FID_INPUT_ISCD=FX@KRWKFTC"),
                eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 원화는_API호출없이_일을_반환한다() {
        ExchangeRateQuote quote = service.getKrwRate("KRW");

        assertThat(quote.krwRate()).isEqualByComparingTo("1");
        verify(restTemplate, never()).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 캐시유효시간에는_KIS를_다시_호출하지_않는다() throws Exception {
        when(accessTokenService.getAccessToken()).thenReturn("access-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(ResponseEntity.ok(exchangeRateResponse()));

        ExchangeRateQuote first = service.getKrwRate("USD");
        ExchangeRateQuote second = service.getKrwRate("USD");

        assertThat(first.cached()).isFalse();
        assertThat(second.cached()).isTrue();
        assertThat(second.stale()).isFalse();
        verify(restTemplate, times(1)).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void 갱신실패시_최근_조회환율을_사용한다() throws Exception {
        KisApiProperties properties = properties();
        properties.setQuoteCacheTtl(Duration.ZERO);
        properties.setStaleQuoteTtl(Duration.ofHours(24));
        service = new KisExchangeRateService(restTemplate, properties, accessTokenService);
        when(accessTokenService.getAccessToken()).thenReturn("access-token");
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class),
                eq(JsonNode.class)))
                .thenReturn(ResponseEntity.ok(exchangeRateResponse()))
                .thenThrow(new ResourceAccessException("timeout"));

        service.getKrwRate("USD");
        ExchangeRateQuote fallback = service.getKrwRate("USD");

        assertThat(fallback.krwRate()).isEqualByComparingTo("1468.50");
        assertThat(fallback.cached()).isTrue();
        assertThat(fallback.stale()).isTrue();
        verify(restTemplate, times(2)).exchange(
                anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(JsonNode.class));
    }

    private KisApiProperties properties() {
        KisApiProperties properties = new KisApiProperties();
        properties.setBaseUrl("https://example.test");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        return properties;
    }

    private JsonNode exchangeRateResponse() throws Exception {
        return objectMapper.readTree("""
                {
                  "rt_cd": "0",
                  "output1": {
                    "ovrs_nmix_prpr": "1,468.50"
                  }
                }
                """);
    }
}
