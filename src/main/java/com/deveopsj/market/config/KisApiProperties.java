package com.deveopsj.market.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "kis.api")
public class KisApiProperties {

    private String baseUrl;
    private String appKey;
    private String appSecret;
    private Duration timeout = Duration.ofSeconds(5);
    private Duration quoteCacheTtl = Duration.ofSeconds(60);
    private Duration staleQuoteTtl = Duration.ofHours(24);
}
