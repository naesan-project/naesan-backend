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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import com.naesan.share.adapter.in.web.PublicVerificationApiController;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferRequestApiIntegrationTest {
    private static final int CONCURRENT_ACCEPT_COUNT = 20;
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
                .andExpect(jsonPath("$[0].product.name").value("생각등대"))
                .andExpect(jsonPath("$[0].product.merchantName").value("생각상점"))
                .andExpect(jsonPath("$[0].status").value("PENDING"));
        mockMvc.perform(get("/api/transfers/incoming")
                        .with(authentication(recipientAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(requestId.toString()))
                .andExpect(jsonPath("$[0].product.name").value("생각등대"))
                .andExpect(jsonPath("$[0].product.merchantName").value("생각상점"));
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

    @Test
    @DisplayName("recipient 수락은 holder를 변경하고 기존 공개 share를 폐기한다")
    void acceptsTransferRequestAndRevokesShare() throws Exception {
        String rawShareToken = issuePublicShare();
        UUID requestId = createTransferRequest();

        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(recipientAuthentication())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(recipientAuthentication()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(recipientAuthentication()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_PENDING"));
        mockMvc.perform(get("/api/transfers/outgoing")
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ACCEPTED"));
        mockMvc.perform(get(
                                "/api/passports/{passportId}/ownership-history",
                                PASSPORT_ID
                        )
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PASSPORT_NOT_FOUND"));
        mockMvc.perform(get(
                                "/api/passports/{passportId}/ownership-history",
                                PASSPORT_ID
                        )
                        .with(authentication(recipientAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].reason").value("ISSUED"))
                .andExpect(jsonPath("$[0].previousHolderAccountId").doesNotExist())
                .andExpect(jsonPath("$[0].newHolderAccountId")
                        .value(OWNER_ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[1].reason").value("TRANSFERRED"))
                .andExpect(jsonPath("$[1].previousHolderAccountId")
                        .value(OWNER_ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[1].newHolderAccountId")
                        .value(RECIPIENT_ACCOUNT_ID.toString()));
        mockMvc.perform(get("/api/public/passport-verification")
                        .header(
                                PublicVerificationApiController.SHARE_TOKEN_HEADER,
                                rawShareToken
                        ))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PUBLIC_SHARE_NOT_FOUND"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_holder_account_id FROM passports WHERE id = ?",
                UUID.class,
                PASSPORT_ID
        )).isEqualTo(RECIPIENT_ACCOUNT_ID);
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ownership_history
                WHERE passport_id = ? AND reason = 'TRANSFERRED'
                """,
                Integer.class,
                PASSPORT_ID
        )).isOne();
    }

    @Test
    @DisplayName("동시 accept API는 한 건만 성공하고 나머지는 같은 409를 반환한다")
    void returnsStableConflictForConcurrentAcceptLosers() throws Exception {
        UUID requestId = createTransferRequest();
        CountDownLatch workersReady = new CountDownLatch(CONCURRENT_ACCEPT_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AcceptApiOutcome>> attempts = new ArrayList<>();

        try (ExecutorService executor =
                Executors.newFixedThreadPool(CONCURRENT_ACCEPT_COUNT)) {
            for (int count = 0; count < CONCURRENT_ACCEPT_COUNT; count++) {
                attempts.add(executor.submit(
                        () -> acceptAfterStart(requestId, workersReady, start)
                ));
            }

            boolean allWorkersReady = workersReady.await(10, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allWorkersReady).isTrue();

            List<AcceptApiOutcome> outcomes = new ArrayList<>();
            for (Future<AcceptApiOutcome> attempt : attempts) {
                outcomes.add(attempt.get(20, TimeUnit.SECONDS));
            }

            assertThat(outcomes)
                    .filteredOn(AcceptApiOutcome.SUCCESS::equals)
                    .hasSize(1);
            assertThat(outcomes)
                    .filteredOn(AcceptApiOutcome.NOT_PENDING::equals)
                    .hasSize(CONCURRENT_ACCEPT_COUNT - 1);
        }

        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_holder_account_id FROM passports WHERE id = ?",
                UUID.class,
                PASSPORT_ID
        )).isEqualTo(RECIPIENT_ACCOUNT_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM passports WHERE id = ?",
                Long.class,
                PASSPORT_ID
        )).isOne();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM transfer_requests WHERE id = ?",
                String.class,
                requestId
        )).isEqualTo("ACCEPTED");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM ownership_history
                WHERE passport_id = ? AND reason = 'TRANSFERRED'
                """,
                Integer.class,
                PASSPORT_ID
        )).isOne();
    }

    private AcceptApiOutcome acceptAfterStart(
            UUID requestId,
            CountDownLatch workersReady,
            CountDownLatch start
    ) throws Exception {
        workersReady.countDown();
        start.await();
        MvcResult result = mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(recipientAuthentication()))
                        .with(csrf()))
                .andReturn();
        if (result.getResponse().getStatus() == 204) {
            return AcceptApiOutcome.SUCCESS;
        }

        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        String code = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.code"
        );
        assertThat(code).isEqualTo("TRANSFER_NOT_PENDING");
        return AcceptApiOutcome.NOT_PENDING;
    }

    @Test
    @DisplayName("만료된 요청은 recipient도 수락할 수 없다")
    void rejectsExpiredTransferAcceptance() throws Exception {
        UUID requestId = insertExpiredRequest();

        mockMvc.perform(post(
                                "/api/transfers/{requestId}/acceptance",
                                requestId
                        )
                        .with(authentication(recipientAuthentication()))
                        .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TRANSFER_NOT_PENDING"));
    }

    private String issuePublicShare() throws Exception {
        MvcResult result = mockMvc.perform(post(
                                "/api/passports/{passportId}/shares",
                                PASSPORT_ID
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"capability\":\"SUMMARY\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.rawToken");
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

    private UUID insertExpiredRequest() {
        Instant expiredAt = Instant.now().minus(1, ChronoUnit.DAYS);
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO transfer_requests (
                    id, passport_id, requester_account_id, recipient_account_id,
                    status, version, expires_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, 'PENDING', 0, ?, ?, ?)
                """,
                requestId,
                PASSPORT_ID,
                OWNER_ACCOUNT_ID,
                RECIPIENT_ACCOUNT_ID,
                expiredAt.atOffset(ZoneOffset.UTC),
                expiredAt.minus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC),
                expiredAt.minus(7, ChronoUnit.DAYS).atOffset(ZoneOffset.UTC)
        );
        return requestId;
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
        jdbcTemplate.update(
                """
                INSERT INTO ownership_history (
                    id, passport_id, previous_holder_account_id,
                    new_holder_account_id, reason, changed_at
                )
                VALUES (?, ?, NULL, ?, 'ISSUED', ?)
                """,
                UUID.randomUUID(),
                PASSPORT_ID,
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

    private enum AcceptApiOutcome {
        SUCCESS,
        NOT_PENDING
    }
}
