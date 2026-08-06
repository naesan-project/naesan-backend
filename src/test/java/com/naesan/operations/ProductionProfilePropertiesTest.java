package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionProfilePropertiesTest {

    @Test
    @DisplayName("Production DB와 frontend와 S3 식별자는 환경 변수 경계로만 받는다")
    void usesEnvironmentBackedConfiguration() throws IOException {
        Properties properties = productionProperties();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${NAESAN_DB_URL}")
                .containsEntry(
                        "spring.datasource.username",
                        "${NAESAN_DB_USERNAME}"
                )
                .containsEntry(
                        "spring.datasource.password",
                        "${NAESAN_DB_PASSWORD}"
                )
                .containsEntry(
                        "naesan.security.frontend-origin",
                        "${NAESAN_FRONTEND_ORIGIN}"
                )
                .containsEntry(
                        "naesan.security.token.jwt-secret",
                        "${NAESAN_AUTH_JWT_SECRET}"
                )
                .containsEntry(
                        "naesan.security.token.refresh-cookie-secure",
                        "true"
                )
                .containsEntry(
                        "naesan.storage.s3.bucket",
                        "${NAESAN_S3_BUCKET}"
                )
                .containsEntry(
                        "naesan.storage.s3.region",
                        "${NAESAN_S3_REGION}"
                )
                .containsEntry(
                        "naesan.storage.s3.endpoint",
                        "${NAESAN_S3_ENDPOINT:}"
                )
                .containsEntry(
                        "naesan.storage.s3.path-style",
                        "${NAESAN_S3_PATH_STYLE:false}"
                )
                .containsEntry(
                        "naesan.storage.s3.server-side-encryption",
                        "${NAESAN_S3_SERVER_SIDE_ENCRYPTION:}"
                );
    }

    @Test
    @DisplayName("Production 서버 포트와 DB connection pool은 무료 컨테이너 한도에 맞춘다")
    void usesRenderPortAndBoundedDatabasePool() throws IOException {
        Properties properties = productionProperties();

        assertThat(properties)
                .containsEntry("server.port", "${PORT:8080}")
                .containsEntry(
                        "spring.datasource.hikari.maximum-pool-size",
                        "${NAESAN_DB_MAXIMUM_POOL_SIZE:5}"
                )
                .containsEntry(
                        "spring.datasource.hikari.minimum-idle",
                        "${NAESAN_DB_MINIMUM_IDLE:0}"
                )
                .containsEntry(
                        "spring.datasource.hikari.connection-timeout",
                        "${NAESAN_DB_CONNECTION_TIMEOUT:30000}"
                )
                .containsEntry(
                        "spring.datasource.hikari.idle-timeout",
                        "${NAESAN_DB_IDLE_TIMEOUT:600000}"
                )
                .containsEntry(
                        "spring.datasource.hikari.max-lifetime",
                        "${NAESAN_DB_MAX_LIFETIME:1800000}"
                );
    }

    @Test
    @DisplayName("Production console log는 MDC를 포함하는 Logstash JSON을 사용한다")
    void usesStructuredConsoleLogging() throws IOException {
        Properties properties = productionProperties();

        assertThat(properties)
                .containsEntry(
                        "logging.structured.format.console",
                        "logstash"
                );
    }

    @Test
    @DisplayName("Production metric은 별도 management port의 Prometheus로만 노출한다")
    void exposesPrometheusOnDedicatedManagementPort() throws IOException {
        Properties properties = productionProperties();

        assertThat(properties)
                .containsEntry(
                        "management.server.port",
                        "${NAESAN_MANAGEMENT_PORT:9090}"
                )
                .containsEntry(
                        "management.endpoints.web.exposure.include",
                        "health,prometheus"
                );
    }

    private Properties productionProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream content = getClass().getResourceAsStream(
                "/application-production.properties"
        )) {
            properties.load(content);
        }
        return properties;
    }
}
