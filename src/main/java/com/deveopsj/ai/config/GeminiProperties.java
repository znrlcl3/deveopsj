package com.deveopsj.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "spring.ai.gemini")
public class GeminiProperties {

    private String apiKey;
    private String baseUrl;
    private String model;

    public String generateContentUrl() {
        String normalizedBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
        return normalizedBaseUrl + "/" + model + ":generateContent?key=" + apiKey;
    }
}
