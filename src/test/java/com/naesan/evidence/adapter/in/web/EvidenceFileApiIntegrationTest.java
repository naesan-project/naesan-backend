package com.naesan.evidence.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest(properties = "naesan.storage.local.root=build/test-storage/file-api")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceFileApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("b6245934-942e-4d59-b80c-4a368078b920");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("796647f4-442d-4eed-a51c-ff35734facdd");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceFileApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareDraft() {
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'file-api@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                now.atOffset(ZoneOffset.UTC)
        );
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
                now.atOffset(ZoneOffset.UTC),
                now.atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("owner가 PDF를 업로드하면 private metadata만 반환한다")
    void attachesFileForOwner() throws Exception {
        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", EVIDENCE_ID)
                        .file(pdfFile())
                        .with(authentication(authenticatedOwner()))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.state").value("TEMPORARY"))
                .andExpect(jsonPath("$.mediaType").value("application/pdf"))
                .andExpect(jsonPath("$.size").value(16))
                .andExpect(jsonPath("$.sha256").doesNotExist())
                .andExpect(jsonPath("$.objectKey").doesNotExist());
    }

    @Test
    @DisplayName("선언한 형식과 파일 내용이 다르면 400으로 거절한다")
    void rejectsMismatchedFile() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "receipt.png",
                "image/png",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", EVIDENCE_ID)
                        .file(file)
                        .with(authentication(authenticatedOwner()))
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_MISMATCH"));
    }

    @Test
    @DisplayName("다른 계정의 Evidence 파일 업로드는 404로 숨긴다")
    void hidesEvidenceFromOtherAccount() throws Exception {
        AuthenticatedAccount otherAccount = new AuthenticatedAccount(
                UUID.randomUUID(),
                "other@example.com",
                Instant.now()
        );

        mockMvc.perform(multipart("/api/evidence/{evidenceId}/file", EVIDENCE_ID)
                        .file(pdfFile())
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                otherAccount,
                                null,
                                List.of()
                        )))
                        .with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    private MockMultipartFile pdfFile() {
        return new MockMultipartFile(
                "file",
                "receipt.pdf",
                "application/pdf",
                "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8)
        );
    }

    private UsernamePasswordAuthenticationToken authenticatedOwner() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(
                        OWNER_ACCOUNT_ID,
                        "file-api@example.com",
                        Instant.now()
                ),
                null,
                List.of()
        );
    }
}
