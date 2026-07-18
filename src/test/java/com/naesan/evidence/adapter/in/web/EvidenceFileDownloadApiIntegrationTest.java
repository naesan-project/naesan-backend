package com.naesan.evidence.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest(properties = "naesan.storage.local.root=build/test-storage/download-api")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceFileDownloadApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("d4c13596-4fbf-46cb-b89d-fcdceae98d0b");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("60544f5f-ebc0-43f3-869a-fd5bf295da45");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("519c373e-709f-497a-946d-9539bc06df8a");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceFileDownloadApiIntegrationTest(
            MockMvc mockMvc,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareFile() throws Exception {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        insertAccount(OWNER_ACCOUNT_ID, "download-owner@example.com");
        insertAccount(OTHER_ACCOUNT_ID, "download-other@example.com");
        insertDraft();
        uploadFile();
    }

    @Test
    @DisplayName("소유자는 구매 증빙 파일을 원본 bytes로 내려받는다")
    void downloadsOwnedEvidenceFile() throws Exception {
        byte[] response = mockMvc.perform(get(
                                "/api/evidence/{evidenceId}/file",
                                EVIDENCE_ID
                        )
                        .with(authentication(authenticatedAccount(
                                OWNER_ACCOUNT_ID,
                                "download-owner@example.com"
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        HttpHeaders.CONTENT_TYPE,
                        "application/pdf"
                ))
                .andExpect(header().string(
                        HttpHeaders.CONTENT_LENGTH,
                        String.valueOf(PDF_CONTENT.length)
                ))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(response).isEqualTo(PDF_CONTENT);
    }

    @Test
    @DisplayName("다른 계정의 구매 증빙 파일은 존재하지 않는 것처럼 응답한다")
    void hidesFileFromOtherAccount() throws Exception {
        mockMvc.perform(get("/api/evidence/{evidenceId}/file", EVIDENCE_ID)
                        .with(authentication(authenticatedAccount(
                                OTHER_ACCOUNT_ID,
                                "download-other@example.com"
                        ))))
                .andExpect(status().isNotFound());
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

    private void insertDraft() {
        Instant createdAt = Instant.parse("2026-07-17T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO purchase_evidence (
                    id, owner_account_id, merchant_name, product_name, purchased_at,
                    amount, currency, state, created_at, updated_at
                )
                VALUES (?, ?, '생각상점', '생각등대', ?, ?, 'KRW', 'DRAFT', ?, ?)
                """,
                EVIDENCE_ID,
                OWNER_ACCOUNT_ID,
                LocalDate.parse("2026-07-01"),
                new BigDecimal("1000.00"),
                createdAt.atOffset(ZoneOffset.UTC),
                createdAt.atOffset(ZoneOffset.UTC)
        );
    }

    private void uploadFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                PDF_CONTENT
        );
        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", EVIDENCE_ID)
                        .file(file)
                        .with(authentication(authenticatedAccount(
                                OWNER_ACCOUNT_ID,
                                "download-owner@example.com"
                        )))
                        .with(csrf()))
                .andExpect(status().isCreated());
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
