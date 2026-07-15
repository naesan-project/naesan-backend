package com.naesan.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class EmailTest {

    @Test
    @DisplayName("이메일의 앞뒤 공백과 대문자를 정규화한다")
    void normalizesEmail() {
        Email email = new Email("  User@Example.COM  ");

        assertThat(email.value()).isEqualTo("user@example.com");
    }

    @Test
    @DisplayName("정규화 결과가 같으면 같은 이메일이다")
    void comparesNormalizedEmail() {
        Email email = new Email("User@Example.COM");
        Email sameEmail = new Email("user@example.com");

        assertThat(email).isEqualTo(sameEmail);
    }

    @Test
    @DisplayName("null 이메일은 생성할 수 없다")
    void rejectsNullEmail() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            "example.com",
            "@example.com",
            "user@",
            "user@@example.com",
            "user name@example.com",
            "user\texample@example.com"
    })
    @DisplayName("구조가 잘못되었거나 공백과 제어 문자가 포함된 이메일은 생성할 수 없다")
    void rejectsInvalidEmail(String value) {
        assertThatThrownBy(() -> new Email(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ASCII가 아닌 문자가 포함된 이메일은 생성할 수 없다")
    void rejectsNonAsciiEmail() {
        assertThatThrownBy(() -> new Email("사용자@example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("254 byte 이메일은 생성할 수 있다")
    void acceptsEmailWith254Bytes() {
        String value = "a".repeat(242) + "@example.com";

        assertThat(value.getBytes(StandardCharsets.UTF_8)).hasSize(254);
        assertThat(new Email(value).value()).isEqualTo(value);
    }

    @Test
    @DisplayName("254 byte를 초과하는 이메일은 생성할 수 없다")
    void rejectsEmailLongerThan254Bytes() {
        String value = "a".repeat(243) + "@example.com";

        assertThat(value.getBytes(StandardCharsets.UTF_8)).hasSize(255);
        assertThatThrownBy(() -> new Email(value))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
