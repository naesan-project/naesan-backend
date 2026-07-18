package com.naesan.evidence.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.security.AuthenticatedAccount;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
class EvidenceDraftApiIntegrationTest {
    private static final String EVIDENCE_API = "/api/evidence";
    private static final UUID OWNER_ACCOUNT_ID =
            UUID.fromString("96762a71-cb21-421b-813f-633ce84941c3");
    private static final String BCRYPT_HASH = "$2a$12$" + "a".repeat(53);

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    EvidenceDraftApiIntegrationTest(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
        this.mockMvc = mockMvc;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void prepareOwnerAccount() {
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
        jdbcTemplate.update(
                """
                INSERT INTO accounts (id, email, password_hash, status, created_at)
                VALUES (?, 'api-owner@example.com', ?, 'ACTIVE', ?)
                """,
                OWNER_ACCOUNT_ID,
                BCRYPT_HASH,
                Instant.parse("2026-07-18T00:00:00Z").atOffset(ZoneOffset.UTC)
        );
    }

    @Test
    @DisplayName("인증 계정의 구매 증빙 draft를 생성한다")
    void createsDraftForAuthenticatedAccount() throws Exception {
        mockMvc.perform(post(EVIDENCE_API)
                        .with(authentication(authenticatedOwner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        org.hamcrest.Matchers.startsWith(EVIDENCE_API + "/")
                ))
                .andExpect(jsonPath("$.state").value("DRAFT"))
                .andExpect(jsonPath("$.merchantName").value("생각상점"))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.currency").value("KRW"));

        UUID savedOwnerId = jdbcTemplate.queryForObject(
                "SELECT owner_account_id FROM purchase_evidence",
                UUID.class
        );
        assertThat(savedOwnerId).isEqualTo(OWNER_ACCOUNT_ID);
    }

    @Test
    @DisplayName("인증되지 않은 draft 생성 요청을 401로 거절한다")
    void rejectsUnauthenticatedRequest() throws Exception {
        mockMvc.perform(post(EVIDENCE_API)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF token이 없는 draft 생성 요청을 403으로 거절한다")
    void rejectsRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(EVIDENCE_API)
                        .with(authentication(authenticatedOwner()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("잘못된 구매 정보를 400 JSON 오류로 반환한다")
    void rejectsInvalidRequest() throws Exception {
        mockMvc.perform(post(EVIDENCE_API)
                        .with(authentication(authenticatedOwner()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "merchantName": "",
                                  "productName": "생각등대",
                                  "purchasedAt": "2026-07-01",
                                  "amount": 1000.00,
                                  "currency": "KRW"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.merchantName")
                        .value("구매처를 입력해 주세요."));
    }

    private UsernamePasswordAuthenticationToken authenticatedOwner() {
        AuthenticatedAccount account = new AuthenticatedAccount(
                OWNER_ACCOUNT_ID,
                "api-owner@example.com",
                Instant.now()
        );
        return UsernamePasswordAuthenticationToken.authenticated(
                account,
                null,
                List.of()
        );
    }

    private String validRequest() {
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
}
