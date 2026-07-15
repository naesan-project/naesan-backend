package com.naesan.account.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(PasswordEncoderConfiguration.class)
class PasswordEncoderConfigurationTest {
    private static final String RAW_PASSWORD = "password1234";

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("비밀번호를 BCrypt cost 12 hash로 변환한다")
    void encodesPasswordWithBcryptCost12() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(passwordHash).matches("^\\$2[ayb]\\$12\\$[./A-Za-z0-9]{53}$");
        assertThat(passwordHash).isNotEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("같은 비밀번호도 random salt로 서로 다른 hash를 생성한다")
    void generatesDifferentHashesForSamePassword() {
        String firstPasswordHash = passwordEncoder.encode(RAW_PASSWORD);
        String secondPasswordHash = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(firstPasswordHash).isNotEqualTo(secondPasswordHash);
    }

    @Test
    @DisplayName("저장된 hash와 비밀번호의 일치 여부를 검증한다")
    void matchesRawPasswordWithStoredHash() {
        String passwordHash = passwordEncoder.encode(RAW_PASSWORD);

        assertThat(passwordEncoder.matches(RAW_PASSWORD, passwordHash)).isTrue();
        assertThat(passwordEncoder.matches("different-password", passwordHash)).isFalse();
    }
}
