package com.naesan.evidence.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest(properties =
        "naesan.storage.local.root=build/test-storage/evidence-security")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceSecurityApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("a1f52373-cb05-41ba-8405-eb7843ec87fd");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("b16e697f-0a9c-4f16-81be-45f3aabfd70e");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceSecurityApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareAccounts() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "security-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "security-other@example.com");
    }

    @Test
    @DisplayName("타인과 비회원에게 private Evidence 조회를 숨긴다")
    void protectsPrivateEvidenceFromOtherActors() throws Exception {
        UUID evidenceId = createDraft(ownerAuthentication());
        attachFile(evidenceId, ownerAuthentication())
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/evidence")
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/evidence/{evidenceId}", evidenceId)
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/evidence/{evidenceId}/file", evidenceId)
                        .with(authentication(otherAuthentication())))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/evidence/{evidenceId}", evidenceId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Evidence API는 내부 key와 digest와 canonical payload를 노출하지 않는다")
    void hidesInternalVerificationMaterial() throws Exception {
        UUID evidenceId = createDraft(ownerAuthentication());

        attachFile(evidenceId, ownerAuthentication())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objectKey").doesNotExist())
                .andExpect(jsonPath("$.sha256").doesNotExist());
        mockMvc.perform(get("/api/evidence/{evidenceId}", evidenceId)
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.file.objectKey").doesNotExist())
                .andExpect(jsonPath("$.file.sha256").doesNotExist());
        confirm(evidenceId, ownerAuthentication())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotDigest").doesNotExist())
                .andExpect(jsonPath("$.canonicalPayload").doesNotExist());
    }

    @Test
    @DisplayName("모든 Evidence 상태 변경 요청에 CSRF token을 요구한다")
    void requiresCsrfForEvidenceCommands() throws Exception {
        mockMvc.perform(post("/api/evidence")
                        .with(authentication(ownerAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceRequest()))
                .andExpect(status().isForbidden());
        UUID evidenceId = createDraft(ownerAuthentication());
        mockMvc.perform(put(
                                "/api/evidence/{evidenceId}/metadata",
                                evidenceId
                        )
                        .with(authentication(ownerAuthentication()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceRequest()))
                .andExpect(status().isForbidden());
        attachFileWithoutCsrf(evidenceId, ownerAuthentication())
                .andExpect(status().isForbidden());
        attachFile(evidenceId, ownerAuthentication())
                .andExpect(status().isCreated());
        mockMvc.perform(post(
                                "/api/evidence/{evidenceId}/confirm",
                                evidenceId
                        )
                        .with(authentication(ownerAuthentication())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("확정된 Evidence는 정보와 파일을 변경할 수 없다")
    void preventsChangesAfterConfirmation() throws Exception {
        UUID evidenceId = createDraft(ownerAuthentication());
        attachFile(evidenceId, ownerAuthentication())
                .andExpect(status().isCreated());
        confirm(evidenceId, ownerAuthentication())
                .andExpect(status().isOk());

        mockMvc.perform(put(
                                "/api/evidence/{evidenceId}/metadata",
                                evidenceId
                        )
                        .with(authentication(ownerAuthentication()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceRequest()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_EDITABLE"));
        attachFile(evidenceId, ownerAuthentication())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_EDITABLE"));
    }

    private UUID createDraft(
            UsernamePasswordAuthenticationToken authentication
    ) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/evidence")
                        .with(authentication(authentication))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidenceRequest()))
                .andExpect(status().isCreated())
                .andReturn();
        String evidenceId = JsonPath.read(
                result.getResponse().getContentAsString(),
                "$.id"
        );
        return UUID.fromString(evidenceId);
    }

    private ResultActions attachFile(
            UUID evidenceId,
            UsernamePasswordAuthenticationToken authentication
    ) throws Exception {
        return mockMvc.perform(multipart(
                                "/api/evidence/{evidenceId}/file",
                                evidenceId
                        )
                        .file(pdfFile())
                        .with(authentication(authentication))
                        .with(csrf()));
    }

    private ResultActions attachFileWithoutCsrf(
            UUID evidenceId,
            UsernamePasswordAuthenticationToken authentication
    ) throws Exception {
        return mockMvc.perform(multipart(
                                "/api/evidence/{evidenceId}/file",
                                evidenceId
                        )
                        .file(pdfFile())
                        .with(authentication(authentication)));
    }

    private ResultActions confirm(
            UUID evidenceId,
            UsernamePasswordAuthenticationToken authentication
    ) throws Exception {
        return mockMvc.perform(post(
                                "/api/evidence/{evidenceId}/confirm",
                                evidenceId
                        )
                        .with(authentication(authentication))
                        .with(csrf()));
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8)
        );
    }

    private String evidenceRequest() {
        return """
                {
                  "merchantName": "생각상점",
                  "productName": "생각등대",
                  "serialNumber": "SERIAL-001",
                  "purchasedAt": "2026-07-01",
                  "amount": 1000.00,
                  "currency": "KRW"
                }
                """;
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

    private UsernamePasswordAuthenticationToken ownerAuthentication() {
        return accountAuthentication(
                OWNER_ACCOUNT_ID,
                "security-owner@example.com"
        );
    }

    private UsernamePasswordAuthenticationToken otherAuthentication() {
        return accountAuthentication(
                OTHER_ACCOUNT_ID,
                "security-other@example.com"
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
