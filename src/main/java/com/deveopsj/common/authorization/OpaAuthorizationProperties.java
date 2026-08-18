package com.deveopsj.common.authorization;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.security.opa")
public class OpaAuthorizationProperties {

    private boolean enabled;
    private String baseUrl = "http://localhost:8181";
    private String decisionPath = "/v1/data/deveopsj/authz/allow";
    private Duration timeout = Duration.ofMillis(500);
}
