package com.deveopsj.ai.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GeminiPropertiesTest {

    @Test
    void 설정한_모델명이_실제_API_URL에_사용된다() {
        GeminiProperties properties = new GeminiProperties();
        properties.setBaseUrl("https://example.test/v1/models/");
        properties.setModel("gemini-test-model");
        properties.setApiKey("test-key");

        assertThat(properties.generateContentUrl())
                .isEqualTo("https://example.test/v1/models/gemini-test-model"
                        + ":generateContent?key=test-key");
    }
}
