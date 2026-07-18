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
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;

@JdbcTest
@Import({
        TestcontainersConfiguration.class,
        PurchaseEvidenceJdbcRepository.class
})
class PurchaseEvidenceJdbcRepositoryTest {
    private static final UUID ACCOUNT_ID =
            UUID.fromString("c185ee46-f382-455a-888f-bfa3e4bdd970");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("912f2aee-ea0b-4e5d-86fa-a05240e842f3");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final PurchaseEvidenceJdbcRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PurchaseEvidenceJdbcRepositoryTest(
            PurchaseEvidenceJdbcRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void insertAccount() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'repository@example.com', ?, 'ACTIVE', ?)
                """,
                ACCOUNT_ID,
                BCRYPT_HASH,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("구매 증빙을 저장하고 같은 도메인 상태로 복원한다")
    void savesAndFindsEvidence() {
        PurchaseEvidence evidence = PurchaseEvidence.createDraft(
                EVIDENCE_ID,
                ACCOUNT_ID,
                metadata(),
                CREATED_AT
        );

        repository.save(evidence);

        assertThat(repository.findById(EVIDENCE_ID))
                .contains(evidence)
                .get()
                .satisfies(savedEvidence -> {
                    assertThat(savedEvidence.metadata()).isEqualTo(metadata());
                    assertThat(savedEvidence.createdAt()).isEqualTo(CREATED_AT);
                });
    }

    @Test
    @DisplayName("존재하지 않는 구매 증빙은 빈 결과를 반환한다")
    void returnsEmptyWhenEvidenceDoesNotExist() {
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    private EvidenceMetadata metadata() {
        return new EvidenceMetadata(
                "생각상점",
                "생각등대",
                "SERIAL-001",
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                "KRW"
        );
    }
}
