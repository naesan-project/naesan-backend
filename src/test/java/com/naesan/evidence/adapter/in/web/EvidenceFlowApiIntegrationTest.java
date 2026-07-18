package com.naesan.evidence.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

@SpringBootTest(properties = "naesan.storage.local.root=build/test-storage/flow-api")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceFlowApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("b50b2c68-a7ec-43b3-af22-849eb7e44eef");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceFlowApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareOwner() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'flow-owner@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                Instant.parse("2026-07-18T00:00:00Z").atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("JSON API로 draft 생성부터 idempotent 확정까지 완료한다")
    void completesEvidenceFlow() throws Exception {
        UUID evidenceId = createDraft();
        attachFile(evidenceId);

        MvcResult firstConfirmation = confirm(evidenceId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CONFIRMED"))
                .andExpect(jsonPath("$.snapshotId").isString())
                .andExpect(jsonPath("$.snapshotDigest").doesNotExist())
                .andReturn();
        String firstSnapshotId = jsonValue(firstConfirmation, "$.snapshotId");

        MvcResult secondConfirmation = confirm(evidenceId)
                .andExpect(status().isOk())
                .andReturn();

        assertThat(jsonValue(secondConfirmation, "$.snapshotId"))
                .isEqualTo(firstSnapshotId);
        assertThat(snapshotCount(evidenceId)).isEqualTo(1);
    }

    private UUID createDraft() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/evidence")
                        .with(authentication(authenticatedOwner()))
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

    private void attachFile(UUID evidenceId) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", evidenceId)
                        .file(file)
                        .with(authentication(authenticatedOwner()))
                        .with(csrf()))
                .andExpect(status().isCreated());
    }

    private ResultActions confirm(UUID evidenceId) throws Exception {
        return mockMvc.perform(post("/api/evidence/{evidenceId}/confirm", evidenceId)
                .with(authentication(authenticatedOwner()))
                .with(csrf()));
    }

    private String jsonValue(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    private int snapshotCount(UUID evidenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM evidence_snapshots WHERE evidence_id = ?",
                Integer.class,
                evidenceId
        );
    }

    private UsernamePasswordAuthenticationToken authenticatedOwner() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(
                        OWNER_ACCOUNT_ID,
                        "flow-owner@example.com",
                        Instant.now()
                ),
                null,
                List.of()
        );
    }
}
