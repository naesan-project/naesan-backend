package com.naesan.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceSnapshotTest {
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("13160536-d083-4993-912f-c3471d6e7b32");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("a932c407-89ef-4310-a1bf-371f41257bd7");
    private static final byte[] PAYLOAD = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String DIGEST = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    @DisplayName("Canonical bytes와 digest로 Evidence snapshot을 만든다")
    void createsSnapshot() {
        EvidenceSnapshot snapshot = snapshot();

        assertThat(snapshot.schemaVersion()).isEqualTo(1);
        assertThat(snapshot.canonicalPayload()).isEqualTo(PAYLOAD);
        assertThat(snapshot.snapshotDigest()).isEqualTo(DIGEST);
    }

    @Test
    @DisplayName("외부 byte 배열 변경으로 canonical payload가 바뀌지 않는다")
    void protectsCanonicalPayload() {
        EvidenceSnapshot snapshot = snapshot();

        snapshot.canonicalPayload()[0] = 'x';

        assertThat(snapshot.canonicalPayload()).isEqualTo(PAYLOAD);
    }

    @Test
    @DisplayName("비어 있는 canonical payload를 거절한다")
    void rejectsEmptyPayload() {
        assertThatThrownBy(() -> new EvidenceSnapshot(
                SNAPSHOT_ID,
                EVIDENCE_ID,
                1,
                new byte[0],
                DIGEST,
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Canonical payload는 비어 있을 수 없습니다.");
    }

    private EvidenceSnapshot snapshot() {
        return new EvidenceSnapshot(
                SNAPSHOT_ID,
                EVIDENCE_ID,
                1,
                PAYLOAD,
                DIGEST,
                CREATED_AT
        );
    }
}
