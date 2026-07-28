package com.deveopsj.market.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "krx.api")
public class KrxApiProperties {

    private String baseUrl;
    private String authKey;
    private Duration timeout = Duration.ofSeconds(10);
}
