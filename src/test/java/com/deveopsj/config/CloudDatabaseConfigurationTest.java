package com.deveopsj.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class CloudDatabaseConfigurationTest {
    private final List<PropertySource<?>> sources = load();

    @Test
    void sharedDatabaseStartupDoesNotMutateSchemaOrRunInitializationSql() {
        assertThat(property("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(property("spring.sql.init.mode")).isEqualTo("never");
    }

    @Test
    void connectionUsesTheLocalSshTunnelAndRequiresExternalCredentials() {
        assertThat(property("spring.datasource.url")).isEqualTo(
                "jdbc:mysql://127.0.0.1:${CLOUD_DB_LOCAL_PORT:13306}/${CLOUD_DB_NAME}"
                + "?serverTimezone=UTC&characterEncoding=UTF-8");
        assertThat(property("spring.datasource.username")).isEqualTo("${CLOUD_DB_USERNAME}");
        assertThat(property("spring.datasource.password")).isEqualTo("${CLOUD_DB_PASSWORD}");
        assertThat(property("spring.datasource.driver-class-name")).isEqualTo("com.mysql.cj.jdbc.Driver");
    }

    @Test
    void localApplicationAndSqlLogsDoNotExposeSharedData() {
        assertThat(property("server.address")).isEqualTo("127.0.0.1");
        assertThat(property("spring.jpa.show-sql")).isEqualTo(false);
        assertThat(property("logging.level.org.hibernate.SQL")).isEqualTo("WARN");
        assertThat(property("logging.level.org.hibernate.orm.jdbc.bind")).isEqualTo("WARN");
    }

    private Object property(String name) {
        return sources.stream().map(source -> source.getProperty(name))
                .filter(value -> value != null).findFirst().orElse(null);
    }

    private static List<PropertySource<?>> load() {
        try {
            return new YamlPropertySourceLoader().load(
                    "application-cloud-db", new ClassPathResource("application-cloud-db.yml"));
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

