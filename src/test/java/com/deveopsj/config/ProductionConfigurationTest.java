package com.deveopsj.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {

    private final List<PropertySource<?>> propertySources = loadProductionConfiguration();

    @Test
    void productionProfileUsesEnvironmentVariablesForSecrets() {
        assertThat(property("spring.datasource.url")).isEqualTo("${DB_URL}");
        assertThat(property("spring.datasource.username")).isEqualTo("${DB_USERNAME}");
        assertThat(property("spring.datasource.password")).isEqualTo("${DB_PASSWORD}");
        assertThat(property("spring.ai.gemini.api-key")).isEqualTo("${GEMINI_API_KEY}");
        assertThat(property("krx.api.auth-key")).isEqualTo("${KRX_API_AUTH_KEY}");
        assertThat(property("kis.api.app-key")).isEqualTo("${KIS_API_APP_KEY}");
        assertThat(property("kis.api.app-secret")).isEqualTo("${KIS_API_APP_SECRET}");
        assertThat(property("app.privacy.operator-name"))
                .isEqualTo("${PRIVACY_OPERATOR_NAME}");
        assertThat(property("app.privacy.contact-email"))
                .isEqualTo("${PRIVACY_CONTACT_EMAIL}");
    }

    @Test
    void productionProfileDisablesSchemaMutationAndSensitiveLogs() {
        assertThat(property("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(property("spring.jpa.show-sql")).isEqualTo(false);
        assertThat(property("logging.level.org.hibernate.SQL")).isEqualTo("WARN");
        assertThat(property("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("WARN");
    }

    @Test
    void productionProfileUsesSecureProxyAndSessionSettings() {
        assertThat(property("server.forward-headers-strategy")).isEqualTo("framework");
        assertThat(property("server.servlet.session.cookie.http-only")).isEqualTo(true);
        assertThat(property("server.servlet.session.cookie.secure")).isEqualTo(true);
        assertThat(property("server.servlet.session.cookie.same-site")).isEqualTo("lax");
        assertThat(property("server.servlet.session.timeout"))
                .isEqualTo("${SESSION_TIMEOUT:30m}");
        assertThat(property("spring.web.error.include-message")).isEqualTo("never");
        assertThat(property("spring.web.error.include-binding-errors")).isEqualTo("never");
        assertThat(property("spring.web.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    void productionProfileExposesOnlyHealthWithoutDetails() {
        assertThat(property("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(property("management.endpoint.health.show-details")).isEqualTo("never");
        assertThat(property("management.endpoint.health.probes.enabled")).isEqualTo(true);
    }

    @Test
    void productionProfileControlsRegistrationAndLoginProtectionWithEnvironmentVariables() {
        assertThat(property("app.registration.enabled")).isEqualTo("${REGISTRATION_ENABLED:true}");
        assertThat(property("app.security.login.max-attempts"))
                .isEqualTo("${LOGIN_MAX_ATTEMPTS:5}");
        assertThat(property("app.security.login.block-duration"))
                .isEqualTo("${LOGIN_BLOCK_DURATION:10m}");
    }

    private Object property(String name) {
        return propertySources.stream()
                .map(source -> source.getProperty(name))
                .filter(value -> value != null)
                .findFirst()
                .orElse(null);
    }

    private static List<PropertySource<?>> loadProductionConfiguration() {
        try {
            return new YamlPropertySourceLoader().load(
                    "application-prod",
                    new ClassPathResource("application-prod.yml"));
        } catch (IOException exception) {
            throw new IllegalStateException("운영 설정 파일을 읽을 수 없습니다.", exception);
        }
    }
}
