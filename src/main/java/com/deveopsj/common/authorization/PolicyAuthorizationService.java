package com.deveopsj.common.authorization;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.annotation.JsonProperty;

@Service
public class PolicyAuthorizationService {

    private final RestTemplate restTemplate;
    private final OpaAuthorizationProperties properties;

    public PolicyAuthorizationService(
            @Qualifier("opaRestTemplate") RestTemplate restTemplate,
            OpaAuthorizationProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public void authorize(AuthorizationInput input) {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            OpaResponse response = restTemplate.postForObject(
                    decisionUrl(), new OpaRequest(input), OpaResponse.class);
            if (response == null || !Boolean.TRUE.equals(response.result())) {
                throw new AccessDeniedException("정책에 의해 요청이 거부되었습니다.");
            }
        } catch (AccessDeniedException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new AccessDeniedException(
                    "인가 정책 서버를 확인할 수 없어 요청을 거부했습니다.", exception);
        }
    }

    private String decisionUrl() {
        return properties.getBaseUrl().replaceAll("/+$", "")
                + "/" + properties.getDecisionPath().replaceAll("^/+", "");
    }

    record OpaRequest(AuthorizationInput input) {
    }

    record OpaResponse(Boolean result, @JsonProperty("decision_id") String decisionId) {
    }
}
