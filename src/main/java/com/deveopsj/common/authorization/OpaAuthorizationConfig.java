package com.deveopsj.common.authorization;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class OpaAuthorizationConfig {

    @Bean
    @Qualifier("opaRestTemplate")
    public RestTemplate opaRestTemplate(OpaAuthorizationProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getTimeout());
        requestFactory.setReadTimeout(properties.getTimeout());
        return new RestTemplate(requestFactory);
    }
}
