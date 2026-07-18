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
class EvidenceListApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("033b8101-3010-4b19-80a4-442a2393e3f0");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("55610d2f-a0a2-477f-864f-e1892a583e45");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceListApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareEvidence() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "list-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "list-other@example.com");
        insertEvidence(
                OWNER_ACCOUNT_ID,
                "오래된 구매",
                Instant.parse("2026-07-16T00:00:00Z")
        );
        insertEvidence(
                OTHER_ACCOUNT_ID,
                "다른 사용자 구매",
                Instant.parse("2026-07-18T00:00:00Z")
        );
        insertEvidence(
                OWNER_ACCOUNT_ID,
                "최근 구매",
                Instant.parse("2026-07-17T00:00:00Z")
        );
    }

    @Test
    @DisplayName("인증 계정의 구매 증빙만 최신순으로 조회한다")
    void listsOwnedEvidenceNewestFirst() throws Exception {
        mockMvc.perform(get("/api/evidence")
                        .with(authentication(authenticatedOwner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].productName").value("최근 구매"))
                .andExpect(jsonPath("$[1].productName").value("오래된 구매"));
    }

    @Test
    @DisplayName("인증되지 않은 구매 증빙 목록 요청을 거절한다")
    void rejectsUnauthenticatedListRequest() throws Exception {
        mockMvc.perform(get("/api/evidence"))
                .andExpect(status().isUnauthorized());
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

    private void insertEvidence(UUID ownerAccountId, String productName, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', ?, ?, ?, 'KRW', 'DRAFT', ?, ?)
                """,
                UUID.randomUUID(),
                ownerAccountId,
                productName,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private UsernamePasswordAuthenticationToken authenticatedOwner() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(
                        OWNER_ACCOUNT_ID,
                        "list-owner@example.com",
                        Instant.now()
                ),
                null,
                List.of()
        );
    }
}
