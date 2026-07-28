package com.deveopsj.market.service;

import java.net.URI;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.deveopsj.market.config.KrxApiProperties;
import com.deveopsj.market.dto.KrxEtfResponse;
import com.deveopsj.market.dto.KrxMarket;
import com.deveopsj.market.dto.KrxStockItem;
import com.deveopsj.market.dto.KrxStockResponse;
import com.deveopsj.market.dto.KrxStockSnapshot;

@Service
public class KrxStockService {

    private static final DateTimeFormatter KRX_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final RestTemplate restTemplate;
    private final KrxApiProperties properties;

    public KrxStockService(@Qualifier("krxRestTemplate") RestTemplate restTemplate,
            KrxApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public KrxStockSnapshot fetchLatest(LocalDate requestedDate, KrxMarket market) {
        if (market == KrxMarket.US) {
            throw new IllegalArgumentException("미국주식은 Nasdaq Directory 서비스를 사용해야 합니다.");
        }
        if (requestedDate.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("미래 날짜는 조회할 수 없습니다.");
        }
        for (int daysAgo = 0; daysAgo <= 10; daysAgo++) {
            LocalDate baseDate = requestedDate.minusDays(daysAgo);
            if (baseDate.getDayOfWeek() == DayOfWeek.SATURDAY
                    || baseDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
                continue;
            }
            List<KrxStockItem> stocks = fetch(baseDate, market);
            if (!stocks.isEmpty()) {
                return new KrxStockSnapshot(baseDate, stocks);
            }
        }
        return new KrxStockSnapshot(requestedDate, List.of());
    }

    private List<KrxStockItem> fetch(LocalDate baseDate, KrxMarket market) {
        if (market == KrxMarket.ALL) {
            java.util.ArrayList<KrxStockItem> stocks = new java.util.ArrayList<>();
            stocks.addAll(fetchMarket(baseDate, KrxMarket.KOSPI));
            stocks.addAll(fetchMarket(baseDate, KrxMarket.KOSDAQ));
            stocks.addAll(fetchMarket(baseDate, KrxMarket.ETF));
            return stocks;
        }
        return fetchMarket(baseDate, market);
    }

    private List<KrxStockItem> fetchMarket(LocalDate baseDate, KrxMarket market) {
        URI uri = UriComponentsBuilder
                .fromUriString(normalizedBaseUrl() + "/" + market.getEndpoint())
                .queryParam("basDd", baseDate.format(KRX_DATE))
                .build()
                .toUri();
        HttpHeaders headers = new HttpHeaders();
        headers.set("AUTH_KEY", properties.getAuthKey());
        try {
            if (market == KrxMarket.ETF) {
                KrxEtfResponse response = restTemplate.exchange(
                        uri, HttpMethod.GET, new HttpEntity<>(headers), KrxEtfResponse.class).getBody();
                if (response == null || response.etfs() == null) {
                    return List.of();
                }
                return response.etfs().stream()
                        .map(com.deveopsj.market.dto.KrxEtfItem::toStockItem)
                        .toList();
            }
            KrxStockResponse response = restTemplate.exchange(
                    uri, HttpMethod.GET, new HttpEntity<>(headers), KrxStockResponse.class).getBody();
            return response == null || response.stocks() == null ? List.of() : response.stocks();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new IllegalStateException("KRX 인증키 또는 API 이용 승인을 확인해 주세요.");
        } catch (RestClientException e) {
            throw new IllegalStateException("KRX API 호출에 실패했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private String normalizedBaseUrl() {
        return properties.getBaseUrl().endsWith("/")
                ? properties.getBaseUrl().substring(0, properties.getBaseUrl().length() - 1)
                : properties.getBaseUrl();
    }
}
