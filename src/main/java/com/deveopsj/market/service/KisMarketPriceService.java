package com.deveopsj.market.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
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

import com.deveopsj.assetplan.entity.InvestmentAsset;
import com.deveopsj.assetplan.repository.InvestmentAssetRepository;
import com.deveopsj.market.config.KisApiProperties;
import com.deveopsj.market.dto.MarketPriceQuote;
import tools.jackson.databind.JsonNode;

@Service
public class KisMarketPriceService {

    private static final Logger log = LoggerFactory.getLogger(KisMarketPriceService.class);
    private static final Map<String, String> US_EXCHANGE_CODES = Map.of(
            "NASDAQ", "NAS",
            "NYSE", "NYS",
            "NYSE_ARCA", "AMS",
            "NYSE_AMERICAN", "AMS");

    private final RestTemplate restTemplate;
    private final KisApiProperties properties;
    private final KisAccessTokenService accessTokenService;
    private final InvestmentAssetRepository investmentAssetRepository;
    private final Map<Long, CachedQuote> quoteCache = new ConcurrentHashMap<>();

    public KisMarketPriceService(@Qualifier("kisRestTemplate") RestTemplate restTemplate,
            KisApiProperties properties,
            KisAccessTokenService accessTokenService,
            InvestmentAssetRepository investmentAssetRepository) {
        this.restTemplate = restTemplate;
        this.properties = properties;
        this.accessTokenService = accessTokenService;
        this.investmentAssetRepository = investmentAssetRepository;
    }

    public MarketPriceQuote getQuote(Long investmentAssetId) {
        InvestmentAsset asset = investmentAssetRepository.findByIdAndActiveTrue(investmentAssetId)
                .orElseThrow(() -> new IllegalArgumentException("조회할 종목을 찾을 수 없습니다."));
        CachedQuote cached = quoteCache.get(investmentAssetId);
        Instant now = Instant.now();
        if (cached != null && cached.isWithin(properties.getQuoteCacheTtl(), now)) {
            return cached.quote().asCached(false);
        }
        try {
            MarketPriceQuote quote = "KRW".equals(asset.getCurrency())
                    ? domesticQuote(asset) : overseasQuote(asset);
            quoteCache.put(investmentAssetId, new CachedQuote(quote, now));
            return quote;
        } catch (IllegalStateException e) {
            if (cached != null && cached.isWithin(properties.getStaleQuoteTtl(), now)) {
                log.warn("KIS 현재가 조회 실패로 최근 가격을 사용합니다. assetId={}, symbol={}",
                        asset.getId(), asset.getSymbol());
                return cached.quote().asCached(true);
            }
            throw e;
        }
    }

    private MarketPriceQuote domesticQuote(InvestmentAsset asset) {
        String uri = UriComponentsBuilder
                .fromUriString(normalizedBaseUrl() + "/uapi/domestic-stock/v1/quotations/inquire-price")
                .queryParam("FID_COND_MRKT_DIV_CODE", "J")
                .queryParam("FID_INPUT_ISCD", asset.getSymbol())
                .build()
                .toUriString();
        JsonNode output = request(uri, "FHKST01010100");
        return quote(asset,
                decimal(output, "stck_prpr"),
                decimal(output, "stck_sdpr"),
                decimal(output, "prdy_vrss"),
                decimal(output, "prdy_ctrt"));
    }

    private MarketPriceQuote overseasQuote(InvestmentAsset asset) {
        String exchangeCode = US_EXCHANGE_CODES.get(
                asset.getMarket().trim().toUpperCase(Locale.ROOT));
        if (exchangeCode == null) {
            throw new IllegalArgumentException("현재가 조회를 지원하지 않는 미국 거래소입니다: "
                    + asset.getMarket());
        }
        String uri = UriComponentsBuilder
                .fromUriString(normalizedBaseUrl() + "/uapi/overseas-price/v1/quotations/price")
                .queryParam("AUTH", "")
                .queryParam("EXCD", exchangeCode)
                .queryParam("SYMB", asset.getSymbol())
                .build()
                .toUriString();
        JsonNode output = request(uri, "HHDFS00000300");
        return quote(asset,
                decimal(output, "last"),
                decimal(output, "base"),
                decimal(output, "diff"),
                decimal(output, "rate"));
    }

    private JsonNode request(String uri, String transactionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessTokenService.getAccessToken());
        headers.set("appkey", properties.getAppKey());
        headers.set("appsecret", properties.getAppSecret());
        headers.set("tr_id", transactionId);
        headers.set("custtype", "P");
        try {
            JsonNode response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), JsonNode.class).getBody();
            if (response == null || !"0".equals(response.path("rt_cd").asText())) {
                String message = response == null ? "" : response.path("msg1").asText();
                String messageCode = response == null ? "" : response.path("msg_cd").asText();
                log.warn("KIS 현재가 응답 오류. transactionId={}, code={}, message={}",
                        transactionId, messageCode, message);
                throw new IllegalStateException(message.isBlank()
                        ? "KIS 현재가 응답을 확인해 주세요." : message);
            }
            JsonNode output = response.path("output");
            if (output.isMissingNode() || output.isNull()) {
                throw new IllegalStateException("KIS 현재가 데이터가 비어 있습니다.");
            }
            return output;
        } catch (HttpStatusCodeException e) {
            log.warn("KIS 현재가 HTTP 오류. status={}, transactionId={}",
                    e.getStatusCode().value(), transactionId);
            throw new IllegalStateException("KIS 현재가 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (ResourceAccessException e) {
            log.warn("KIS 현재가 연결 오류. transactionId={}, cause={}",
                    transactionId, e.getMessage());
            throw new IllegalStateException("KIS 현재가 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        } catch (RestClientException e) {
            log.warn("KIS 현재가 호출 오류. transactionId={}, cause={}",
                    transactionId, e.getMessage());
            throw new IllegalStateException("KIS 현재가 조회에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private MarketPriceQuote quote(InvestmentAsset asset, BigDecimal currentPrice,
            BigDecimal previousClose, BigDecimal change, BigDecimal changeRate) {
        if (currentPrice.signum() <= 0) {
            throw new IllegalStateException("현재가가 제공되지 않는 종목입니다.");
        }
        return new MarketPriceQuote(
                asset.getId(), asset.getSymbol(), asset.getAssetName(), asset.getMarket(),
                asset.getCurrency(), currentPrice, previousClose, change, changeRate,
                LocalDateTime.now(), false, false);
    }

    private BigDecimal decimal(JsonNode output, String field) {
        String value = output.path(field).asText();
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("%", "");
        if (normalized.isBlank()
                || "-".equals(normalized)
                || "+".equals(normalized)
                || "N/A".equalsIgnoreCase(normalized)) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("KIS 현재가 숫자 형식을 확인해 주세요: " + field);
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    private record CachedQuote(MarketPriceQuote quote, Instant cachedAt) {

        private boolean isWithin(Duration ttl, Instant now) {
            return ttl != null && !ttl.isNegative() && now.isBefore(cachedAt.plus(ttl));
        }
    }
}
