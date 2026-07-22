package com.naesan.transfer.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferRequestApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("6c0550bc-d4e5-4bb0-9061-f7e1900343f1");
    private static final UUID RECIPIENT_ACCOUNT_ID =
            UUID.fromString("1587136a-873b-4e1f-a2f4-76b44861d66d");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("ef0362a0-8719-4f75-919f-e9635674bd2f");
    private static final UUID INACTIVE_ACCOUNT_ID =
            UUID.fromString("82e0b383-6004-48dd-bd74-92342d820a90");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("f8608777-68fd-4e82-9972-c8cf1576113b");
    private static final UUID SNAPSHOT_ID =
            UUID.fromString("ba2df690-260c-41ee-a104-e3241376bcdc");
    private static final UUID PASSPORT_ID =
            UUID.fromString("736f19f8-9d9c-4464-a7c6-d65ee1cf9afe");
    private static final String OWNER_EMAIL = "transfer-owner@example.com";
    private static final String RECIPIENT_EMAIL = "transfer-recipient@example.com";
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final Instant CREATED_AT = Instant.parse("2026-07-22T00:00:00Z");

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    TransferRequestApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void preparePassport() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
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
        insertAccount(OWNER_ACCOUNT_ID, OWNER_EMAIL, "ACTIVE");
        insertAccount(RECIPIENT_ACCOUNT_ID, RECIPIENT_EMAIL, "ACTIVE");
        insertAccount(OTHER_ACCOUNT_ID, "transfer-other@example.com", "ACTIVE");
        insertAccount(
                INACTIVE_ACCOUNT_ID,
                "transfer-inactive@example.com",
                "DELETION_PENDING"
        );
        insertPassport();
    }

    @AfterEach
    void removeTransferRequests() {
        jdbcTemplate.update("DELETE FROM transfer_requests");
    }

    @Test
    @DisplayName("현재 holder는 활성 계정의 email로 이전을 요청한다")
    void createsTransferRequest() throws Exception {
        mockMvc.perform(createRequest(ownerAuthentication(), RECIPIENT_EMAIL))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passportId").value(PASSPORT_ID.toString()))
                .andExpect(jsonPath("$.recipientEmail").value(RECIPIENT_EMAIL))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.requesterAccountId").doesNotExist())
                .andExpect(jsonPath("$.recipientAccountId").doesNotExist());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_requests WHERE status = 'PENDING'",
                Integer.class
        )).isOne();
    }

    @Test
    @DisplayName("만료된 기존 요청을 EXPIRED로 바꾸고 새 요청을 만든다")
    void replacesExpiredRequest() throws Exception {
        insertExpiredRequest();

        mockMvc.perform(get("/api/transfers/incoming")
                        .with(authentication(recipientAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("EXPIRED"));

        mockMvc.perform(createRequest(ownerAuthentication(), RECIPIENT_EMAIL))
                .andExpect(status().isCreated());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_requests WHERE status = 'EXPIRED'",
                Integer.class
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_requests WHERE status = 'PENDING'",
                Integer.class
        )).isOne();
    }

    @Test
    @DisplayName("holder·recipient·단일 요청 규칙과 API 보안을 지킨다")
    void enforcesTransferRequestRules() throws Exception {
        mockMvc.perform(createRequest(otherAuthentication(), RECIPIENT_EMAIL))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_FOUND"));
        mockMvc.perform(createRequest(
                        ownerAuthentication(),
                        "transfer-inactive@example.com"
                ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TRANSFER_RECIPIENT_NOT_FOUND"));
        mockMvc.perform(createRequest(ownerAuthentication(), OWNER_EMAIL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("TRANSFER_SELF_REQUEST"));

        mockMvc.perform(createRequest(ownerAuthentication(), RECIPIENT_EMAIL))
                .andExpect(status().isCreated());
        mockMvc.perform(createRequest(ownerAuthentication(), RECIPIENT_EMAIL))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_ALREADY_PENDING"));

        mockMvc.perform(post("/api/passports/{passportId}/transfers", PASSPORT_ID)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientEmail\":\"" + RECIPIENT_EMAIL + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/passports/{passportId}/transfers", PASSPORT_ID)
                        .with(authentication(ownerAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientEmail\":\"" + RECIPIENT_EMAIL + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("참여자는 incoming·outgoing을 조회하고 수신자는 요청을 거절한다")
    void listsAndRejectsTransferRequest() throws Exception {
        UUID requestId = createTransferRequest();

        mockMvc.perform(get("/api/transfers/outgoing")
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].requesterEmail").value(OWNER_EMAIL))
                .andExpect(jsonPath("$[0].recipientEmail").value(RECIPIENT_EMAIL))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mockMvc.perform(get("/api/transfers/incoming")
                        .with(authentication(recipientAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()));
        mockMvc.perform(get("/api/transfers/incoming")
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post(
                                "/api/transfers/{requestId}/rejection",
                                requestId
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                                "/api/transfers/{requestId}/rejection",
                                requestId
                        )
                        .with(authentication(recipientAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/transfers/outgoing")
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("REJECTED"));
        mockMvc.perform(delete("/api/transfers/{requestId}", requestId)
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_PENDING"));
    }

    @Test
    @DisplayName("요청자만 대기 중인 요청을 취소할 수 있다")
    void cancelsTransferRequest() throws Exception {
        UUID requestId = createTransferRequest();

        mockMvc.perform(delete("/api/transfers/{requestId}", requestId)
                        .with(authentication(otherAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/transfers/{requestId}", requestId)
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transfer_requests WHERE id = ?",
                String.class,
                requestId
        )).isEqualTo("CANCELLED");
    }

    private UUID createTransferRequest() throws Exception {
        MvcResult result = mockMvc.perform(
                        createRequest(ownerAuthentication(), RECIPIENT_EMAIL)
                )
                .andExpect(status().isCreated())
                .andReturn();
        String requestId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return UUID.fromString(requestId);
    }

    private MockHttpServletRequestBuilder createRequest(
            UsernamePasswordAuthenticationToken authentication,
            String recipientEmail
    ) {
        return post("/api/passports/{passportId}/transfers", PASSPORT_ID)
                .with(authentication(authentication))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientEmail\":\"" + recipientEmail + "\"}");
    }

    private void insertExpiredRequest() {
        Instant expiredAt = Instant.now().minus(1, ChronoUnit.DAYS);
        jdbcTemplate.update(
                """
                INSERT INTO transfer_requests (
                    id, passport_id, requester_account_id, recipient_account_id,
                    status, version, expires_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """,
                UUID.randomUUID(),
                PASSPORT_ID,
                OWNER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                expiredAt.atOffset(ZoneOffset.UTC),
                expiredAt.minus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                expiredAt.minus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)
        );
    }

    private void insertAccount(UUID accountId, String email, String status) {
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                accountId,
                email,
                BCRYPT_HASH,
                status,
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
        return accountAuthentication(OWNER_ACCOUNT_ID, OWNER_EMAIL);
    }

    private UsernamePasswordAuthenticationToken otherAuthentication() {
        return accountAuthentication(
                OTHER_ACCOUNT_ID,
                "transfer-other@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken recipientAuthentication() {
        return accountAuthentication(RECIPIENT_ACCOUNT_ID, RECIPIENT_EMAIL);
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
