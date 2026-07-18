package com.naesan.evidence.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceFileType;
import com.naesan.evidence.domain.StorageKey;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        EvidenceFileJdbcRepository.class
})
class EvidenceFileJdbcRepositoryTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("63f56d96-62c4-465c-9894-df0830d77bbc");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("af1b0993-2505-467d-8e71-931ae12bdbd6");
    private static final UUID FILE_ID =
            UUID.fromString("aa9cc426-bcc2-4aa3-a70c-a4c59286f5d0");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final EvidenceFileJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceFileJdbcRepositoryTest(
            EvidenceFileJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'file-repository@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'DRAFT', ?, ?)
                """,
                EVIDENCE_ID,
                ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("Evidence 파일 metadata를 저장하고 복원한다")
    void savesAndFindsEvidenceFile() {
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                new StorageKey("temporary/file"),
                "a".repeat(64),
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );

        repository.save(evidenceFile);

        assertThat(repository.findByEvidenceId(EVIDENCE_ID))
                .contains(evidenceFile)
                .get()
                .satisfies(savedFile -> {
                    assertThat(savedFile.objectKey()).isEqualTo(evidenceFile.objectKey());
                    assertThat(savedFile.sha256()).isEqualTo(evidenceFile.sha256());
                });
    }

    @Test
    @DisplayName("파일이 없는 Evidence는 빈 결과를 반환한다")
    void returnsEmptyWhenFileDoesNotExist() {
        assertThat(repository.findByEvidenceId(EVIDENCE_ID)).isEmpty();
    }

    @Test
    @DisplayName("승격된 파일의 permanent key와 상태를 갱신한다")
    void updatesPromotedFile() {
        EvidenceFile temporaryFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                new StorageKey("temporary/file"),
                "a".repeat(64),
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );
        repository.save(temporaryFile);
        EvidenceFile promotedFile = temporaryFile.promote(
                new StorageKey("permanent/file"),
                CREATED_AT.plusSeconds(1)
        );

        repository.update(promotedFile);

        assertThat(repository.findByEvidenceId(EVIDENCE_ID))
                .contains(promotedFile)
                .get()
                .satisfies(savedFile -> assertThat(savedFile.objectKey())
                        .isEqualTo(new StorageKey("permanent/file")));
    }

    @Test
    @DisplayName("삭제되지 않은 파일의 저장소 key만 조회한다")
    void findsReferencedObjectKeys() {
        EvidenceFile evidenceFile = EvidenceFile.createTemporary(
                FILE_ID,
                EVIDENCE_ID,
                new StorageKey("temporary/file"),
                "a".repeat(64),
                EvidenceFileType.PDF,
                1024,
                CREATED_AT
        );
        repository.save(evidenceFile);

        assertThat(repository.findAllObjectKeys())
                .containsExactly(new StorageKey("temporary/file"));
    }
}
