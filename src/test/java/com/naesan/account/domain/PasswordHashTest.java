package com.naesan.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordHashTest {
    private static final String BCRYPT_BODY = "a".repeat(53);
    private static final String VALID_BCRYPT_HASH = "$2a$12$" + BCRYPT_BODY;

    @ParameterizedTest
    @ValueSource(strings = {"2a", "2y", "2b"})
    @DisplayName("BCrypt 2a, 2y, 2b version의 cost 12 해시를 허용한다")
    void acceptsBcryptCost12Hash(String version) {
        String value = "$" + version + "$12$" + BCRYPT_BODY;

        PasswordHash passwordHash = new PasswordHash(value);

        assertThat(passwordHash.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("null 비밀번호 해시를 거절한다")
    void rejectsNullHash() {
        assertThatThrownBy(() -> new PasswordHash(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "plain-password",
            "$2a$10$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "$2a$12$aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa!"
    })
    @DisplayName("BCrypt cost 12 형식이 아닌 비밀번호 해시를 거절한다")
    void rejectsInvalidBcryptCost12Hash(String value) {
        assertThatThrownBy(() -> new PasswordHash(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("같은 해시는 동일한 value다")
    void comparesHashByValue() {
        PasswordHash passwordHash = new PasswordHash(VALID_BCRYPT_HASH);
        PasswordHash samePasswordHash = new PasswordHash(VALID_BCRYPT_HASH);

        assertThat(passwordHash).isEqualTo(samePasswordHash);
        assertThat(passwordHash.hashCode()).isEqualTo(samePasswordHash.hashCode());
    }

    @Test
    @DisplayName("문자열 표현에 비밀번호 해시를 노출하지 않는다")
    void redactsHashFromStringRepresentation() {
        PasswordHash passwordHash = new PasswordHash(VALID_BCRYPT_HASH);

        assertThat(passwordHash.toString())
                .isEqualTo("PasswordHash[REDACTED]")
                .doesNotContain(VALID_BCRYPT_HASH);
    }
}
