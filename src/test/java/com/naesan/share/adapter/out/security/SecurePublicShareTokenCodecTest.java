package com.naesan.share.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.share.application.port.out.GeneratedPublicShareToken;

class SecurePublicShareTokenCodecTest {
    private final SecurePublicShareTokenCodec tokenCodec =
            new SecurePublicShareTokenCodec();

    @Test
    @DisplayName("256-bit 난수 token을 URL-safe 43자로 발급하고 SHA-256 hash를 만든다")
    void generatesTokenAndHash() {
        GeneratedPublicShareToken token = tokenCodec.generate();

        assertThat(token.rawToken()).hasSize(43)
                .matches("[A-Za-z0-9_-]{43}");
        assertThat(token.tokenHash()).hasSize(32);
        assertThat(tokenCodec.hash(token.rawToken()))
                .hasValueSatisfying(hash -> assertThat(hash)
                        .containsExactly(token.tokenHash()));
    }

    @Test
    @DisplayName("반복 발급한 raw token은 중복되지 않는다")
    void generatesUniqueTokens() {
        Set<String> rawTokens = new HashSet<>();

        for (int count = 0; count < 1_000; count++) {
            rawTokens.add(tokenCodec.generate().rawToken());
        }

        assertThat(rawTokens).hasSize(1_000);
    }

    @Test
    @DisplayName("정해진 형식이 아닌 token은 hash lookup 입력으로 허용하지 않는다")
    void rejectsMalformedTokens() {
        assertThat(tokenCodec.hash(null)).isEmpty();
        assertThat(tokenCodec.hash("short-token")).isEmpty();
        assertThat(tokenCodec.hash("a".repeat(42) + "+")).isEmpty();
    }
}
