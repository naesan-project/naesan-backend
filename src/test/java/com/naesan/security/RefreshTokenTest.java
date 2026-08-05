package com.naesan.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RefreshTokenTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-08-06T00:00:00Z");

    @Test
    @DisplayName("유효한 refresh token은 1회 사용 시각을 기록한다")
    void consumesActiveRefreshToken() {
        RefreshToken token = token();
        Instant consumedAt = ISSUED_AT.plusSeconds(60);

        RefreshToken consumed = token.consume(consumedAt);

        assertThat(consumed.consumedAt()).isEqualTo(consumedAt);
        assertThat(consumed.isActiveAt(consumedAt.plusSeconds(1))).isFalse();
    }

    @Test
    @DisplayName("이미 사용했거나 만료된 refresh token은 다시 사용할 수 없다")
    void rejectsConsumedOrExpiredRefreshToken() {
        RefreshToken consumed = token().consume(ISSUED_AT.plusSeconds(60));

        assertThatThrownBy(() -> consumed.consume(ISSUED_AT.plusSeconds(61)))
                .isInstanceOf(TokenSessionException.class);
        assertThatThrownBy(() -> token().consume(ISSUED_AT.plusSeconds(3_600)))
                .isInstanceOf(TokenSessionException.class);
    }

    @Test
    @DisplayName("폐기한 refresh token은 만료 전이어도 사용할 수 없다")
    void revokesRefreshToken() {
        RefreshToken revoked = token().revoke(ISSUED_AT.plusSeconds(30));

        assertThat(revoked.revokedAt()).isEqualTo(ISSUED_AT.plusSeconds(30));
        assertThat(revoked.isActiveAt(ISSUED_AT.plusSeconds(31))).isFalse();
    }

    private RefreshToken token() {
        return RefreshToken.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new byte[32],
                ISSUED_AT,
                ISSUED_AT.plusSeconds(3_600)
        );
    }
}
