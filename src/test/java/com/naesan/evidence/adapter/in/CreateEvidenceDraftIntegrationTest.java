package com.naesan.evidence.adapter.in;

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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.application.CreateEvidenceDraftCommand;
import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.PurchaseEvidence;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CreateEvidenceDraftIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("293a2c29-cbb0-44dc-a251-4419f74bc4ec");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final CreateEvidenceDraftService service;
    private final PurchaseEvidenceRepository repository;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    CreateEvidenceDraftIntegrationTest(
            CreateEvidenceDraftService service,
            PurchaseEvidenceRepository repository,
            JdbcTemplate jdbcTemplate
    ) {
        this.service = service;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void insertOwnerAccount() {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'draft-owner@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                Instant.parse("2026-07-18T00:00:00Z").atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("구매 증빙 draft 생성 흐름을 PostgreSQL까지 연결한다")
    void createsDraftInPostgreSql() {
        PurchaseEvidence evidence = service.create(new CreateEvidenceDraftCommand(
                OWNER_ACCOUNT_ID,
                "생각상점",
                "생각등대",
                "SERIAL-001",
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                "KRW"
        ));

        assertThat(repository.findById(evidence.id()))
                .contains(evidence);
    }
}
