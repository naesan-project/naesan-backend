package com.naesan.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(ints = {12, 64})
    @DisplayName("12 byte와 64 byte 비밀번호를 허용한다")
    void acceptsBoundaryLengthPassword(int byteLength) {
        String rawPassword = "a".repeat(byteLength);

        assertThatCode(() -> PasswordPolicy.validate(rawPassword))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {11, 65})
    @DisplayName("12~64 byte 범위를 벗어난 비밀번호를 거절한다")
    void rejectsPasswordOutsideByteRange(int byteLength) {
        String rawPassword = "a".repeat(byteLength);

        assertThatThrownBy(() -> PasswordPolicy.validate(rawPassword))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null 비밀번호를 거절한다")
    void rejectsNullPassword() {
        assertThatThrownBy(() -> PasswordPolicy.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("문자 수가 아닌 UTF-8 byte 수로 검증한다")
    void validatesUtf8ByteLength() {
        String rawPassword = "가".repeat(4);

        assertThat(rawPassword).hasSize(4);
        assertThat(rawPassword.getBytes(StandardCharsets.UTF_8)).hasSize(12);
        assertThatCode(() -> PasswordPolicy.validate(rawPassword))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("문자 수가 64 이하라도 UTF-8 기준 64 byte를 초과하면 거절한다")
    void rejectsMultibytePasswordLongerThan64Bytes() {
        String rawPassword = "가".repeat(22);

        assertThat(rawPassword).hasSize(22);
        assertThat(rawPassword.getBytes(StandardCharsets.UTF_8)).hasSize(66);
        assertThatThrownBy(() -> PasswordPolicy.validate(rawPassword))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("byte 길이가 유효하면 공백 비밀번호를 허용한다")
    void acceptsWhitespacePassword() {
        String rawPassword = " ".repeat(12);

        assertThatCode(() -> PasswordPolicy.validate(rawPassword))
                .doesNotThrowAnyException();
    }
}
