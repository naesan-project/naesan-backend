package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProductionEnvironmentGuardTest {

    @Test
    @DisplayName("HTTPS frontend와 S3, Secure cookie, 중지된 미구성 proof를 허용한다")
    void acceptsSafeUnconfiguredProductionEnvironment() {
        assertThatCode(() -> new ProductionEnvironmentGuard(
                "https://naesan.example.com",
                "s3",
                true,
                "unconfigured",
                false
        )).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("HTTP frontend와 local storage와 insecure cookie를 각각 거절한다")
    void rejectsUnsafeWebAndStorageSettings() {
        assertThatThrownBy(() -> new ProductionEnvironmentGuard(
                "http://naesan.example.com",
                "s3",
                true,
                "unconfigured",
                false
        )).hasMessage("Production frontend origin은 HTTPS여야 합니다.");
        assertThatThrownBy(() -> new ProductionEnvironmentGuard(
                "https://naesan.example.com",
                "local",
                true,
                "unconfigured",
                false
        )).hasMessage("Production profile은 S3 storage를 사용해야 합니다.");
        assertThatThrownBy(() -> new ProductionEnvironmentGuard(
                "https://naesan.example.com",
                "s3",
                false,
                "unconfigured",
                false
        )).hasMessage("Production session cookie는 Secure여야 합니다.");
    }

    @Test
    @DisplayName("provider 없는 proof worker 실행을 거절한다")
    void rejectsUnsafeProofSettings() {
        assertThatThrownBy(() -> new ProductionEnvironmentGuard(
                "https://naesan.example.com",
                "s3",
                true,
                "unconfigured",
                true
        )).hasMessage(
                "Proof provider가 미구성일 때 worker를 실행할 수 없습니다."
        );
    }
}
