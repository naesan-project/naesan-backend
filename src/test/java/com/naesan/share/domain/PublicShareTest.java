package com.naesan.share.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicShareTest {
    private static final Instant CREATED_AT = Instant.parse("2026-07-21T00:00:00Z");

    @Test
    @DisplayName("만료 전 revoke되지 않은 share만 사용할 수 있다")
    void checksAvailability() {
        PublicShare share = issueShare();

        assertThat(share.isAvailableAt(CREATED_AT.plus(6, ChronoUnit.DAYS)))
                .isTrue();
        assertThat(share.isAvailableAt(CREATED_AT.plus(7, ChronoUnit.DAYS)))
                .isFalse();
        assertThat(share.revoke(CREATED_AT.plus(1, ChronoUnit.DAYS))
                .isAvailableAt(CREATED_AT.plus(2, ChronoUnit.DAYS)))
                .isFalse();
    }

    @Test
    @DisplayName("token hash는 외부 변경으로부터 보호한다")
    void protectsTokenHash() {
        byte[] tokenHash = new byte[32];
        PublicShare share = PublicShare.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                tokenHash,
                PublicShareCapability.SUMMARY,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );

        tokenHash[0] = 1;
        byte[] exposedHash = share.tokenHash();
        exposedHash[1] = 1;

        assertThat(share.tokenHash()).containsOnly(0);
    }

    @Test
    @DisplayName("32 byte가 아닌 token hash와 잘못된 만료 시각은 거부한다")
    void rejectsInvalidShare() {
        assertThatThrownBy(() -> PublicShare.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new byte[31],
                PublicShareCapability.SUMMARY,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PublicShare.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new byte[32],
                PublicShareCapability.SUMMARY,
                CREATED_AT,
                CREATED_AT
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private PublicShare issueShare() {
        return PublicShare.issue(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new byte[32],
                PublicShareCapability.FILE_MATCH,
                CREATED_AT.plus(7, ChronoUnit.DAYS),
                CREATED_AT
        );
    }
}
