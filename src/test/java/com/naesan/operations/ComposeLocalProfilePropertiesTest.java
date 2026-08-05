package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ComposeLocalProfilePropertiesTest {

    @Test
    @DisplayName("Compose local profile은 HTTP origin과 비보안 refresh cookie를 사용한다")
    void usesLocalHttpSecurityBoundary() throws IOException {
        Properties properties = composeLocalProperties();

        assertThat(properties)
                .containsEntry(
                        "naesan.security.frontend-origin",
                        "${NAESAN_FRONTEND_ORIGIN:http://localhost:8080}"
                )
                .containsEntry(
                        "naesan.security.token.refresh-cookie-secure",
                        "false"
                );
    }

    @Test
    @DisplayName("Compose local profile은 컨테이너 DB와 S3 설정을 환경 변수로 받는다")
    void usesContainerBackedPersistence() throws IOException {
        Properties properties = composeLocalProperties();

        assertThat(properties)
                .containsEntry("spring.datasource.url", "${NAESAN_DB_URL}")
                .containsEntry("naesan.storage.provider", "s3")
                .containsEntry(
                        "naesan.storage.s3.endpoint",
                        "${NAESAN_S3_ENDPOINT:}"
                );
    }

    private Properties composeLocalProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream content = getClass().getResourceAsStream(
                "/application-compose-local.properties"
        )) {
            properties.load(content);
        }
        return properties;
    }
}
