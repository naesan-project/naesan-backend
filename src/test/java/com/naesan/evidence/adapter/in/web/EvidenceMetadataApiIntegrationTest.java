package com.naesan.evidence.adapter.in.web;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EvidenceMetadataApiIntegrationTest {
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("20d3817a-a8f7-426b-9e4f-1e592ddff646");
    private static final UUID OTHER_ACCOUNT_ID =
            UUID.fromString("9c15d0bc-ef3a-4121-a481-c459fb45f985");
    private static final UUID EVIDENCE_ID =
            UUID.fromString("d5a22870-e7a5-45fd-8969-38f4446be48d");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceMetadataApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareDraft() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        Instant now = Instant.parse("2026-07-18T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'metadata-api@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                now.atOffset(ZoneOffset.UTC)
        );
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'metadata-other@example.com', ?, 'ACTIVE', ?)
                """,
                OTHER_ACCOUNT_ID,
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
    @DisplayName("owner가 구매 증빙 metadata를 수정한다")
    void updatesMetadataForOwner() throws Exception {
        mockMvc.perform(put("/api/evidence/{evidenceId}/metadata", EVIDENCE_ID)
                        .with(authentication(authenticatedOwner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantName").value("새 상점"))
                .andExpect(jsonPath("$.productName").value("새 제품"))
                .andExpect(jsonPath("$.amount").value(2000.00));
    }

    @Test
    @DisplayName("다른 계정의 metadata 수정 요청을 404로 숨긴다")
    void hidesEvidenceFromOtherAccount() throws Exception {
        AuthenticatedAccount otherAccount = new AuthenticatedAccount(
                OTHER_ACCOUNT_ID,
                "metadata-other@example.com",
                Instant.now()
        );

        mockMvc.perform(put("/api/evidence/{evidenceId}/metadata", EVIDENCE_ID)
                        .with(authentication(UsernamePasswordAuthenticationToken.authenticated(
                                otherAccount,
                                null,
                                List.of()
                        )))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EVIDENCE_NOT_FOUND"));
    }

    private UsernamePasswordAuthenticationToken authenticatedOwner() {
        return UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccount(
                        OWNER_ACCOUNT_ID,
                        "metadata-api@example.com",
                        Instant.now()
                ),
                null,
                List.of()
        );
    }

    private String updateRequest() {
        return """
                {
                  "merchantName": "새 상점",
                  "productName": "새 제품",
                  "serialNumber": null,
                  "purchasedAt": "2026-07-02",
                  "amount": 2000.00,
                  "currency": "KRW"
                }
                """;
    }
}
