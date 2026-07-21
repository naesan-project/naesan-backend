package com.naesan.share.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
class PublicShareManagementApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("8a46a631-6710-41be-a309-6267af78d857");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("1a7c84e2-f634-4981-853c-308f54015a4e");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("e6e4d128-d7a1-4ef5-aadc-d62a045152e8");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("46bff33e-4dd8-4e80-af0f-3cc3b2a4b9d6");
    private static final UUID PASSPORT_ID =
            UUID.fromString("6877443c-323e-4ce7-8fa7-41faaf699d3c");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-21T00:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PublicShareManagementApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update("DELETE FROM public_shares");
        jdbcTemplate.update("DELETE FROM outbox_reprocess_audit");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "share-api-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "share-api-other@example.com");
        insertPassport();
    }

    @Test
    @DisplayName("현재 보유자는 JSON API로 share를 발급하고 회전한 뒤 폐기한다")
    void managesShareLifecycle() throws Exception {
        MvcResult issuance = issueShare("SUMMARY");
        String firstRawToken = jsonValue(issuance, "$.rawToken");
        UUID firstShareId = UUID.fromString(jsonValue(issuance, "$.id"));

        MvcResult rotation = rotateShare("FILE_MATCH");
        String rotatedRawToken = jsonValue(rotation, "$.rawToken");
        UUID rotatedShareId = UUID.fromString(jsonValue(rotation, "$.id"));

        assertThat(rotatedRawToken).isNotEqualTo(firstRawToken);
        assertThat(revokedAt(firstShareId)).isNotNull();
        mockMvc.perform(delete(
                                "/api/passports/{passportId}/shares/{shareId}",
                                PASSPORT_ID,
                                rotatedShareId
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(revokedAt(rotatedShareId)).isNotNull();
    }

    @Test
    @DisplayName("발급 응답은 raw token만 한 번 제공하고 hash와 내부 정보는 노출하지 않는다")
    void exposesOnlyOneTimeRawToken() throws Exception {
        issueShare("SUMMARY")
                .getResponse();

        MvcResult issuance = rotateShare("SUMMARY");

        assertThat(jsonValue(issuance, "$.rawToken")).hasSize(43);
        assertThat(issuance.getResponse().getContentAsString())
                .doesNotContain("tokenHash")
                .doesNotContain(OWNER_ACCOUNT_ID.toString())
                .doesNotContain("share-api-owner@example.com");
    }

    @Test
    @DisplayName("타 계정과 인증·CSRF 없는 요청은 share를 변경할 수 없다")
    void enforcesManagementSecurity() throws Exception {
        mockMvc.perform(post("/api/passports/{passportId}/shares", PASSPORT_ID)
                        .with(authentication(otherAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"SUMMARY\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLIC_SHARE_NOT_FOUND"));
        mockMvc.perform(post("/api/passports/{passportId}/shares", PASSPORT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"SUMMARY\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/passports/{passportId}/shares", PASSPORT_ID)
                        .with(authentication(ownerAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"SUMMARY\"}"))
                .andExpect(status().isForbidden());
    }

    private MvcResult issueShare(String capability) throws Exception {
        return mockMvc.perform(post("/api/passports/{passportId}/shares", PASSPORT_ID)
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"" + capability + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capability").value(capability))
                .andExpect(jsonPath("$.rawToken").isString())
                .andExpect(jsonPath("$.tokenHash").doesNotExist())
                .andReturn();
    }

    private MvcResult rotateShare(String capability) throws Exception {
        return mockMvc.perform(post(
                                "/api/passports/{passportId}/shares/rotation",
                                PASSPORT_ID
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"" + capability + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.capability").value(capability))
                .andExpect(jsonPath("$.rawToken").isString())
                .andReturn();
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private Instant revokedAt(UUID shareId) {
        return jdbcTemplate.queryForObject(
                "SELECT revoked_at FROM public_shares WHERE id = ?",
                (resultSet, rowNumber) -> resultSet
                        .getObject(1, java.time.OffsetDateTime.class)
                        .toInstant(),
                shareId
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

    private void insertPassport() {
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at, confirmed_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'CONFIRMED', ?, ?, ?)
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
        jdbcTemplate.update(
                """
                INSERT INTO passports (
                    id, snapshot_id, current_holder_account_id,
                    status, version, created_at
                )
                VALUES (?, ?, ?, 'ACTIVE', 0, ?)
                """,
                PASSPORT_ID,
                SNAPSHOT_ID,
                OWNER_ACCOUNT_ID,
                CREATED_AT.atOffset(ZoneOffset.UTC)
        );
    }

    private UsernamePasswordAuthenticationToken ownerAuthentication() {
        return accountAuthentication(
                OWNER_ACCOUNT_ID,
                "share-api-owner@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken otherAuthentication() {
        return accountAuthentication(
                OTHER_ACCOUNT_ID,
                "share-api-other@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken accountAuthentication(
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
