package com.naesan.passport.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PassportQueryApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("c2a0ff83-3182-4664-a7f4-905b289a1c90");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("bed36fb4-6d47-4c5c-a056-f8a2c20cf1f2");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("b53004a1-ea5e-4938-996c-0767a65dd0dd");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("9a93e41a-af42-4fe2-b03b-e98304ad2c8a");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PassportQueryApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareIssuedPassport() throws Exception {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "passport-query-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "passport-query-other@example.com");
        insertConfirmedSnapshot();
    }

    @Test
    @DisplayName("현재 보유자가 Passport 목록과 상세에서 business와 proof 상태를 분리해 본다")
    void readsPassportListAndDetails() throws Exception {
        UUID passportId = issuePassport();

        mockMvc.perform(get("/api/passports")
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(passportId.toString()))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$[0].proof.state").value("PREPARED"));
        mockMvc.perform(get("/api/passports/{passportId}", passportId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(passportId.toString()))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("PREPARED"));
    }

    @Test
    @DisplayName("다른 계정은 목록에서 Passport를 보지 못하고 상세는 404를 받는다")
    void hidesPassportFromAnotherAccount() throws Exception {
        UUID passportId = issuePassport();

        mockMvc.perform(get("/api/passports")
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/passports/{passportId}", passportId)
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASSPORT_NOT_FOUND"));
    }

    private UUID issuePassport() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/passports")
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId":"%s"}
                                """.formatted(SNAPSHOT_ID)))
                .andExpect(status().isCreated())
                .andReturn();
        String passportId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return UUID.fromString(passportId);
    }

    private void insertConfirmedSnapshot() {
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

    private UsernamePasswordAuthenticationToken ownerAuthentication() {
        return authenticatedAccount(
                OWNER_ACCOUNT_ID,
                "passport-query-owner@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken otherAuthentication() {
        return authenticatedAccount(
                OTHER_ACCOUNT_ID,
                "passport-query-other@example.com"
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
