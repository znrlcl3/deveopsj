package com.deveopsj.market.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import com.deveopsj.market.config.KisApiProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class KisAccessTokenServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void 발급받은_접근토큰을_만료전까지_재사용한다() throws Exception {
        KisApiProperties properties = new KisApiProperties();
        properties.setBaseUrl("https://example.test/");
        properties.setAppKey("app-key");
        properties.setAppSecret("app-secret");
        JsonNode response = new ObjectMapper().readTree("""
                {"access_token": "token-value", "expires_in": 86400}
                """);
        when(restTemplate.postForObject(
                eq("https://example.test/oauth2/tokenP"),
                any(HttpEntity.class),
                eq(JsonNode.class))).thenReturn(response);
        KisAccessTokenService service = new KisAccessTokenService(restTemplate, properties);

        assertThat(service.getAccessToken()).isEqualTo("token-value");
        assertThat(service.getAccessToken()).isEqualTo("token-value");
        verify(restTemplate, times(1)).postForObject(
                eq("https://example.test/oauth2/tokenP"),
                any(HttpEntity.class),
                eq(JsonNode.class));
    }
}
