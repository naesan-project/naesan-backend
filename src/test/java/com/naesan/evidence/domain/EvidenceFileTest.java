package com.naesan.evidence.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvidenceFileTest {
    private static final UUID FILE_ID =
            UUID.fromString("e8d371fa-9dfd-416f-bfef-948320134231");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("a3e3a43b-dd90-48fd-9edc-0c1bcb677208");
    private static final StorageKey OBJECT_KEY = new StorageKey("temporary/file");
    private static final String SHA256 = "a".repeat(64);
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");

    @Test
    @DisplayName("검증된 업로드 결과로 임시 Evidence 파일을 생성한다")
    void createsTemporaryFile() {
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                SHA256,
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );

        assertThat(evidenceFile.state()).isEqualTo(EvidenceFileState.TEMPORARY);
        assertThat(evidenceFile.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(evidenceFile.sha256()).isEqualTo(SHA256);
        assertThat(evidenceFile.size()).isEqualTo(1024);
    }

    @Test
    @DisplayName("소문자 16진수가 아닌 SHA-256을 거절한다")
    void rejectsInvalidSha256() {
        assertThatThrownBy(() -> EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                "A".repeat(64),
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SHA-256은 64자 소문자 16진수여야 합니다.");
    }

    @Test
    @DisplayName("비어 있는 Evidence 파일을 거절한다")
    void rejectsEmptyFile() {
        assertThatThrownBy(() -> EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                SHA256,
                EvidenceFileType.PDF,
                0,
                CREATED_AT
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Evidence 파일 크기는 0보다 커야 합니다.");
    }

    @Test
    @DisplayName("임시 Evidence 파일을 permanent key로 승격한다")
    void promotesTemporaryFile() {
        EvidenceFile temporaryFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                SHA256,
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );
        StorageKey permanentKey = new StorageKey("permanent/file");
        Instant promotedAt = CREATED_AT.plusSeconds(1);

        EvidenceFile promotedFile = temporaryFile.promote(permanentKey, promotedAt);

        assertThat(promotedFile.state()).isEqualTo(EvidenceFileState.PROMOTED);
        assertThat(promotedFile.objectKey()).isEqualTo(permanentKey);
        assertThat(promotedFile.updatedAt()).isEqualTo(promotedAt);
    }

    @Test
    @DisplayName("파일 삭제 요청과 완료 상태를 순서대로 전이한다")
    void completesDeletionLifecycle() {
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                SHA256,
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );

        EvidenceFile deletionPending = evidenceFile.requestDeletion(
                CREATED_AT.plusSeconds(1)
        );
        EvidenceFile deleted = deletionPending.completeDeletion(
                CREATED_AT.plusSeconds(2)
        );

        assertThat(deletionPending.state())
                .isEqualTo(EvidenceFileState.DELETION_PENDING);
        assertThat(deleted.state()).isEqualTo(EvidenceFileState.DELETED);
    }

    @Test
    @DisplayName("삭제 대기 전에는 삭제 완료할 수 없다")
    void rejectsDeletionCompletionWithoutRequest() {
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                OBJECT_KEY,
                SHA256,
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );

        assertThatThrownBy(() -> evidenceFile.completeDeletion(
                CREATED_AT.plusSeconds(1)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("삭제 대기 파일만 삭제 완료할 수 있습니다.");
    }
}
