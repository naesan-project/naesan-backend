package com.naesan.evidence.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceDetailsApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("09bc18d7-1947-4f12-b60f-d11459257640");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("f8499635-dc41-40aa-a4c4-ef4f72e122c7");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("4c9e6864-5847-4393-83fd-ea38c6b1d46f");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceDetailsApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "details-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "details-other@example.com");
        insertEvidence();
        insertEvidenceFile();
    }

    @Test
    @DisplayName("소유한 구매 증빙과 파일 상태를 상세 조회한다")
    void getsOwnedEvidenceDetails() throws Exception {
        mockMvc.perform(get("/api/evidence/{evidenceId}", EVIDENCE_ID)
                        .with(authentication(authenticatedAccount(
                                OWNER_ACCOUNT_ID,
                                "details-owner@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(EVIDENCE_ID.toString()))
                .andExpect(jsonPath("$.state").value("FILE_ATTACHED"))
                .andExpect(jsonPath("$.productName").value("생각등대"))
                .andExpect(jsonPath("$.file.state").value("TEMPORARY"))
                .andExpect(jsonPath("$.file.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.file.objectKey").doesNotExist())
                .andExpect(jsonPath("$.file.sha256").doesNotExist());
    }

    @Test
    @DisplayName("다른 계정의 구매 증빙은 존재하지 않는 것처럼 응답한다")
    void hidesEvidenceFromOtherAccount() throws Exception {
        mockMvc.perform(get("/api/evidence/{evidenceId}", EVIDENCE_ID)
                        .with(authentication(authenticatedAccount(
                                OTHER_ACCOUNT_ID,
                                "details-other@example.com"
                        ))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    private void insertAccount(UUID accountId, String email) {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, ?, ?, 'ACTIVE', ?)
                """,
                accountId,
                email,
                BCRYPT_HASH,
                Instant.parse("2026-07-15T00:00:00Z").atOffset(ZoneOffset.UTC)
        );
    }

    private void insertEvidence() {
        Instant createdAt = Instant.parse("2026-07-17T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW',
                        'FILE_ATTACHED', 1, ?, ?)
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void insertEvidenceFile() {
        Instant createdAt = Instant.parse("2026-07-17T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO evidence_files (
                    id, evidence_id, object_key, sha256, media_type, size_bytes,
                    state, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'application/pdf', 1024,
                        'TEMPORARY', ?, ?)
                """,
                UUID.randomUUID(),
                EVIDENCE_ID,
                "temporary/" + UUID.randomUUID(),
                "a".repeat(64),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private UsernamePasswordAuthenticationToken authenticatedAccount(
            UUID accountId,
            String email
    ) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(accountId, email, Instant.now()),
                null,
                List.of()
        );
    }
}
