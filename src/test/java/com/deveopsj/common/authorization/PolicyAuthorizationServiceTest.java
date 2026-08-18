package com.deveopsj.common.authorization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class PolicyAuthorizationServiceTest {

    @Test
    void OPA가_true를_반환하면_허용한다() {
        TestContext context = context(true);
        context.server.expect(once(), requestTo(
                        "http://localhost:8181/v1/data/deveopsj/authz/allow"))
                .andExpect(content().json("""
                        {"input":{"subject":{"member_id":7,"role":"USER"},"action":"update",
                        "resource":{"type":"spending","id":10,"owner_id":7}}}
                        """))
                .andRespond(withSuccess("{\"result\":true,\"decision_id\":\"decision-1\"}",
                        MediaType.APPLICATION_JSON));

        assertThatCode(() -> context.service.authorize(input())).doesNotThrowAnyException();
        context.server.verify();
    }

    @Test
    void OPA가_false를_반환하면_거부한다() {
        TestContext context = context(true);
        context.server.expect(once(), requestTo(
                        "http://localhost:8181/v1/data/deveopsj/authz/allow"))
                .andRespond(withSuccess("{\"result\":false}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> context.service.authorize(input()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("정책에 의해 요청이 거부되었습니다.");
        context.server.verify();
    }

    @Test
    void OPA가_응답하지_않으면_fail_closed로_거부한다() {
        TestContext context = context(true);
        context.server.expect(once(), requestTo(
                        "http://localhost:8181/v1/data/deveopsj/authz/allow"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> context.service.authorize(input()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("인가 정책 서버를 확인할 수 없어 요청을 거부했습니다.");
        context.server.verify();
    }

    @Test
    void OPA가_비활성화되면_외부호출없이_기존인가를_사용한다() {
        TestContext context = context(false);

        assertThatCode(() -> context.service.authorize(input())).doesNotThrowAnyException();
        context.server.verify();
    }

    private TestContext context(boolean enabled) {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OpaAuthorizationProperties properties = new OpaAuthorizationProperties();
        properties.setEnabled(enabled);
        return new TestContext(new PolicyAuthorizationService(restTemplate, properties), server);
    }

    private AuthorizationInput input() {
        return new AuthorizationInput(
                new AuthorizationInput.Subject(7L, "USER"),
                "update",
                new AuthorizationInput.Resource("spending", 10L, 7L));
    }

    private record TestContext(
            PolicyAuthorizationService service,
            MockRestServiceServer server) {
    }
}
