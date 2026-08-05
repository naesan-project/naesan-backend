package com.naesan.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecureRefreshTokenCodecTest {
    private final SecureRefreshTokenCodec codec =
            new SecureRefreshTokenCodec(new SecureRandom());

    @Test
    @DisplayName("256-bit refresh token과 SHA-256 hash를 생성한다")
    void generatesRefreshTokenAndHash() {
        GeneratedRefreshToken token = codec.generate();

        assertThat(token.rawToken()).hasSize(43)
                .matches("^[A-Za-z0-9_-]+$");
        assertThat(token.tokenHash()).hasSize(32);
        assertThat(codec.hash(token.rawToken()))
                .hasValueSatisfying(hash -> assertThat(hash)
                        .containsExactly(token.tokenHash()));
    }

    @Test
    @DisplayName("반복 발급한 refresh token은 중복되지 않는다")
    void generatesUniqueRefreshTokens() {
        Set<String> rawTokens = new HashSet<>();

        for (int index = 0; index < 100; index++) {
            rawTokens.add(codec.generate().rawToken());
        }

        assertThat(rawTokens).hasSize(100);
    }

    @Test
    @DisplayName("정해진 형식이 아닌 refresh token은 hash 조회 입력으로 허용하지 않는다")
    void rejectsMalformedRefreshTokens() {
        assertThat(codec.hash(null)).isEmpty();
        assertThat(codec.hash("short-token")).isEmpty();
        assertThat(codec.hash("a".repeat(42) + "+")).isEmpty();
    }
}
