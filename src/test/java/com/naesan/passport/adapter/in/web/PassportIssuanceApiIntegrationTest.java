package com.naesan.passport.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PassportIssuanceApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("0aa3f368-4d49-4bc4-bb89-0035f5211d55");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("a2331ea1-a42a-4bb6-b2f4-d26fc8f98935");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("ae0d3b26-7cbb-4d2d-afcf-cd1607024271");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("cb80a664-6a91-4b61-9878-8d840a480546");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PassportIssuanceApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareConfirmedSnapshot() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "passport-api-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "passport-api-other@example.com");
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, version, created_at, updated_at, confirmed_at
                )
                VALUES (
                    ?, ?, '생각상점', '생각등대', ?,
                    ?, 'KRW', 'CONFIRMED', 1, ?, ?, ?
                )
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO evidence_snapshots (
                    id, evidence_id, schema_version, canonical_payload,
                    snapshot_digest, created_at
                )
                VALUES (?, ?, 1, ?, ?, ?)
                """,
                SNAPSHOT_ID,
                EVIDENCE_ID,
                "{}".getBytes(StandardCharsets.UTF_8),
                "a".repeat(64),
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("owner가 JSON API로 Passport를 발급하고 분리된 proof 상태를 받는다")
    void issuesPassportAsJson() throws Exception {
        mockMvc.perform(post("/api/passports")
                        .with(authentication(authenticatedAccount(
                                OWNER_ACCOUNT_ID,
                                "passport-api-owner@example.com"
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId":"%s"}
                                """.formatted(SNAPSHOT_ID)))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith("/api/passports/")
                ))
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.snapshotId").value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("PREPARED"))
                .andExpect(jsonPath("$.proof.commitment").isString())
                .andExpect(jsonPath("$.proof.anchorSalt").doesNotExist())
                .andExpect(jsonPath("$.snapshotDigest").doesNotExist());
    }

    @Test
    @DisplayName("이미 발급한 snapshot은 stable conflict로 응답한다")
    void rejectsRepeatedIssuance() throws Exception {
        issueAsOwner();

        issueAsOwner()
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PASSPORT_ALREADY_ISSUED"));
    }

    @Test
    @DisplayName("다른 계정의 snapshot은 존재 여부를 숨기는 404로 응답한다")
    void hidesAnotherOwnersSnapshot() throws Exception {
        mockMvc.perform(post("/api/passports")
                        .with(authentication(authenticatedAccount(
                                OTHER_ACCOUNT_ID,
                                "passport-api-other@example.com"
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId":"%s"}
                                """.formatted(SNAPSHOT_ID)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASSPORT_SOURCE_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions issueAsOwner()
            throws Exception {
        return mockMvc.perform(post("/api/passports")
                .with(authentication(authenticatedAccount(
                        OWNER_ACCOUNT_ID,
                        "passport-api-owner@example.com"
                )))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"snapshotId":"%s"}
                        """.formatted(SNAPSHOT_ID)));
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
                CREATED_AT.atOffset(ZoneOffset.UTC)
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
