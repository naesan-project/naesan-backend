package com.naesan.passport.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.naesan.TestcontainersConfiguration;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.ReprocessOutboxEventCommand;
import com.naesan.passport.application.ReprocessProofOutboxService;
import com.naesan.security.AuthenticatedAccount;
import com.naesan.passport.support.ControllableProofAnchorAdapter;
import com.naesan.passport.support.ControllableProofAnchorAdapter.ProofOutcome;

@SpringBootTest(properties =
        "naesan.storage.local.root=build/test-storage/passport-flow")
@AutoConfigureMockMvc
@Import({
        TestcontainersConfiguration.class,
        PassportFlowApiIntegrationTest.ControlledProofConfiguration.class
})
class PassportFlowApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("8599275f-cb77-403b-947f-f91a6639f8c9");
    private static final UUID INACTIVE_ACCOUNT_ID =
            UUID.fromString("a9f89973-b4f1-4842-9a92-d07c09b7b8ae");
    private static final Instant CREATED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final ProcessProofOutboxService processProofOutboxService;
    private final ReprocessProofOutboxService reprocessProofOutboxService;
    private final ControllableProofAnchorAdapter proofAnchorPort;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    PassportFlowApiIntegrationTest(
            MockMvc mockMvc,
            ProcessProofOutboxService processProofOutboxService,
            ReprocessProofOutboxService reprocessProofOutboxService,
            ControllableProofAnchorAdapter proofAnchorPort,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.processProofOutboxService = processProofOutboxService;
        this.reprocessProofOutboxService = reprocessProofOutboxService;
        this.proofAnchorPort = proofAnchorPort;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareAccounts() {
        proofAnchorPort.reset();
        jdbcTemplate.update("DELETE FROM outbox_reprocess_audit");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM proof_anchors");
        jdbcTemplate.update("DELETE FROM ownership_history");
        jdbcTemplate.update("DELETE FROM passports");
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(
                OWNER_ACCOUNT_ID,
                "passport-flow-owner@example.com",
                "ACTIVE"
        );
        insertAccount(
                INACTIVE_ACCOUNT_ID,
                "passport-flow-inactive@example.com",
                "DELETION_PENDING"
        );
    }

    @Test
    @DisplayName("JSON API로 Evidence 확정부터 Passport 외부 증명 완료까지 진행한다")
    void completesPassportProofFlow() throws Exception {
        UUID evidenceId = createEvidenceDraft();
        attachEvidenceFile(evidenceId);
        UUID snapshotId = confirmEvidence(evidenceId);
        MvcResult issuance = issuePassport(snapshotId);
        UUID passportId = UUID.fromString(jsonValue(issuance, "$.id"));
        String commitment = jsonValue(issuance, "$.proof.commitment");

        boolean processed = processProofOutboxService.processNext("flow-worker");

        assertThat(processed).isTrue();
        mockMvc.perform(get("/api/passports/{passportId}", passportId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("CONFIRMED"))
                .andExpect(jsonPath("$.proof.commitment").value(commitment))
                .andExpect(jsonPath("$.proof.anchorSalt").doesNotExist())
                .andExpect(jsonPath("$.snapshotDigest").doesNotExist());
    }

    @Test
    @DisplayName("JSON API 발급 뒤 내부 재처리로 dead letter를 복구한다")
    void recoversDeadLetterWithoutPublicOperationApi() throws Exception {
        UUID evidenceId = createEvidenceDraft();
        attachEvidenceFile(evidenceId);
        UUID snapshotId = confirmEvidence(evidenceId);
        MvcResult issuance = issuePassport(snapshotId);
        UUID passportId = UUID.fromString(jsonValue(issuance, "$.id"));
        proofAnchorPort.setOutcome(ProofOutcome.PERMANENT_FAILURE);

        processProofOutboxService.processNext("flow-worker-1");

        mockMvc.perform(get("/api/passports/{passportId}", passportId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("PREPARED"));

        reprocessProofOutboxService.reprocess(new ReprocessOutboxEventCommand(
                outboxEventId(),
                "local-operator",
                "provider 설정 복구"
        ));
        proofAnchorPort.setOutcome(ProofOutcome.SUCCESS);
        processProofOutboxService.processNext("flow-worker-2");

        mockMvc.perform(get("/api/passports/{passportId}", passportId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("CONFIRMED"));
        assertThat(auditCount()).isOne();
    }

    @Test
    @DisplayName("Passport API는 인증과 CSRF와 활성 계정을 요구한다")
    void enforcesPassportSecurityBoundary() throws Exception {
        mockMvc.perform(get("/api/passports"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/passports")
                        .with(authentication(ownerAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/passports")
                        .with(authentication(inactiveAuthentication())))
                .andExpect(status().isUnauthorized());
    }

    private UUID createEvidenceDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/evidence")
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantName": "생각상점",
                                  "productName": "생각등대",
                                  "serialNumber": "SERIAL-001",
                                  "purchasedAt": "2026-07-01",
                                  "amount": 1000.00,
                                  "currency": "KRW"
                                }
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(jsonValue(result, "$.id"));
    }

    private void attachEvidenceFile(UUID evidenceId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", evidenceId)
                        .file(file)
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private UUID confirmEvidence(UUID evidenceId) throws Exception {
        MvcResult result = mockMvc.perform(post(
                                "/api/evidence/{evidenceId}/confirm",
                                evidenceId
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return UUID.fromString(jsonValue(result, "$.snapshotId"));
    }

    private MvcResult issuePassport(UUID snapshotId) throws Exception {
        return mockMvc.perform(post("/api/passports")
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"snapshotId":"%s"}
                                """.formatted(snapshotId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.proof.state").value("PREPARED"))
                .andReturn();
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private void insertAccount(
            UUID accountId,
            String email,
            String status
    ) {
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

    private UUID outboxEventId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM outbox_events",
                UUID.class
        );
    }

    private int auditCount() {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_reprocess_audit",
                Integer.class
        );
    }

    private UsernamePasswordAuthenticationToken ownerAuthentication() {
        return accountAuthentication(
                OWNER_ACCOUNT_ID,
                "passport-flow-owner@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken inactiveAuthentication() {
        return accountAuthentication(
                INACTIVE_ACCOUNT_ID,
                "passport-flow-inactive@example.com"
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

    @TestConfiguration(proxyBeanMethods = false)
    static class ControlledProofConfiguration {

        @Bean
        @Primary
        ControllableProofAnchorAdapter controllableProofAnchorAdapter() {
            return new ControllableProofAnchorAdapter(Clock.systemUTC());
        }
    }
}
