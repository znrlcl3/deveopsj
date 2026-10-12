package com.deveopsj.market.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.deveopsj.market.config.KisApiProperties;
import com.deveopsj.market.dto.ExchangeRateQuote;

import tools.jackson.databind.JsonNode;

@Service
public class KisExchangeRateService {

    private static final Logger log = LoggerFactory.getLogger(KisExchangeRateService.class);
    private static final String USD = "USD";
    private static final String USD_KRW_SYMBOL = "FX@KRWKFTC";
    private static final String TRANSACTION_ID = "FHKST03030100";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestTemplate restTemplate;
    private final KisApiProperties properties;
    private final KisAccessTokenService accessTokenService;
    private final Map<String, CachedExchangeRate> cache = new ConcurrentHashMap<>();

    public KisExchangeRateService(@Qualifier("kisRestTemplate") RestTemplate restTemplate,
            KisApiProperties properties,
            KisAccessTokenService accessTokenService) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.accessTokenService = accessTokenService;
    }

    public ExchangeRateQuote getKrwRate(String currency) {
        if ("KRW".equals(currency)) {
            return new ExchangeRateQuote("KRW", BigDecimal.ONE, LocalDateTime.now(), false, false);
        }
        if (!USD.equals(currency)) {
            throw new IllegalArgumentException("실시간 환율 조회를 지원하지 않는 통화입니다: " + currency);
        }

        CachedExchangeRate cached = cache.get(currency);
        Instant now = Instant.now();
        if (cached != null && cached.isWithin(properties.getQuoteCacheTtl(), now)) {
            return cached.quote().asCached(false);
        }
        try {
            ExchangeRateQuote quote = requestUsdKrwRate();
            cache.put(currency, new CachedExchangeRate(quote, now));
            return quote;
        } catch (IllegalStateException e) {
            if (cached != null && cached.isWithin(properties.getStaleQuoteTtl(), now)) {
                log.warn("KIS 환율 조회 실패로 최근 환율을 사용합니다. currency={}", currency);
                return cached.quote().asCached(true);
            }
            throw e;
        }
    }

    private ExchangeRateQuote requestUsdKrwRate() {
        LocalDate today = LocalDate.now();
        String uri = UriComponentsBuilder
                .fromUriString(normalizedBaseUrl()
                        + "/uapi/overseas-price/v1/quotations/inquire-daily-chartprice")
                .queryParam("FID_COND_MRKT_DIV_CODE", "X")
                .queryParam("FID_INPUT_ISCD", USD_KRW_SYMBOL)
                .queryParam("FID_INPUT_DATE_1", today.minusDays(7).format(DATE_FORMAT))
                .queryParam("FID_INPUT_DATE_2", today.format(DATE_FORMAT))
                .queryParam("FID_PERIOD_DIV_CODE", "D")
                .build()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenService.getAccessToken());
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", TRANSACTION_ID);
        headers.set("custtype", "P");
        try {
            JsonNode response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            if (response == null || !"0".equals(response.path("rt_cd").asText())) {
                String message = response == null ? "" : response.path("msg1").asText();
                log.warn("KIS 환율 응답 오류. code={}, message={}",
                        response == null ? "" : response.path("msg_cd").asText(), message);
                throw new IllegalStateException(message.isBlank()
                        ? "KIS 환율 응답을 확인해 주세요." : message);
            }
            BigDecimal rate = decimal(response.path("output1"), "ovrs_nmix_prpr");
            if (rate.signum() <= 0) {
                throw new IllegalStateException("원/달러 환율이 제공되지 않았습니다.");
            }
            return new ExchangeRateQuote(USD, rate, LocalDateTime.now(), false, false);
        } catch (HttpStatusCodeException e) {
            log.warn("KIS 환율 HTTP 오류. status={}", e.getStatusCode().value());
            throw new IllegalStateException("KIS 환율 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (ResourceAccessException e) {
            log.warn("KIS 환율 연결 오류. cause={}", e.getMessage());
            throw new IllegalStateException("KIS 환율 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("KIS 환율 호출 오류. cause={}", e.getMessage());
            throw new IllegalStateException("KIS 환율 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private BigDecimal decimal(JsonNode output, String field) {
        String value = output.path(field).asText();
        try {
            return value == null || value.isBlank()
                    ? BigDecimal.ZERO : new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalStateException("KIS 환율 숫자 형식을 확인해 주세요: " + field);
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private record CachedExchangeRate(ExchangeRateQuote quote, Instant cachedAt) {

        private boolean isWithin(java.time.Duration ttl, Instant now) {
            return ttl != null && !ttl.isNegative() && now.isBefore(cachedAt.plus(ttl));
        }
    }
}
