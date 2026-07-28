package com.deveopsj.market.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "nasdaq.directory")
public class NasdaqDirectoryProperties {

    private String nasdaqListedUrl;
    private String otherListedUrl;
}
