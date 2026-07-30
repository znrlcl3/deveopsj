package com.deveopsj.market.service;

import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.market.config.KisApiProperties;
import tools.jackson.databind.JsonNode;

@Service
public class KisAccessTokenService {

    private static final long EXPIRY_MARGIN_SECONDS = 60;

    private final RestTemplate restTemplate;
    private final KisApiProperties properties;
    private String accessToken;
    private Instant expiresAt = Instant.EPOCH;

    public KisAccessTokenService(@Qualifier("kisRestTemplate") RestTemplate restTemplate,
            KisApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public synchronized String getAccessToken() {
        if (accessToken != null
                && Instant.now().isBefore(expiresAt.minusSeconds(EXPIRY_MARGIN_SECONDS))) {
            return accessToken;
        }
        validateConfiguration();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> body = Map.of(
                "grant_type", "client_credentials",
                "appkey", properties.getAppKey(),
                "appsecret", properties.getAppSecret());
        try {
            JsonNode response = restTemplate.postForObject(
                    normalizedBaseUrl() + "/oauth2/tokenP",
                    new HttpEntity<>(body, headers),
                    JsonNode.class);
            if (response == null || response.path("access_token").asText().isBlank()) {
                throw new IllegalStateException("KIS 접근 토큰 응답을 확인해 주세요.");
            }
            accessToken = response.path("access_token").asText();
            long expiresIn = response.path("expires_in").asLong(86_400);
            expiresAt = Instant.now().plusSeconds(Math.max(expiresIn, 120));
            return accessToken;
        } catch (RestClientException e) {
            throw new IllegalStateException("KIS 인증에 실패했습니다. 앱키와 앱시크릿을 확인해 주세요.");
        }
    }

    private void validateConfiguration() {
        if (properties.getBaseUrl() == null || properties.getBaseUrl().isBlank()
                || properties.getAppKey() == null || properties.getAppKey().isBlank()
                || properties.getAppSecret() == null || properties.getAppSecret().isBlank()) {
            throw new IllegalStateException("KIS API 설정이 필요합니다.");
        }
    }

    private String normalizedBaseUrl() {
        String baseUrl = properties.getBaseUrl().trim();
        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }
}
