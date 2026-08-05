package com.naesan.account.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.AccountErrorCode;
import com.naesan.account.application.AccountException;
import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;
import com.naesan.evidence.application.AttachEvidenceFileService;
import com.naesan.evidence.application.CreateEvidenceDraftCommand;
import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.domain.EvidenceFileState;
import com.naesan.security.AuthenticatedAccount;
import com.naesan.security.TokenSession;
import com.naesan.security.TokenSessionException;
import com.naesan.security.TokenSessionManager;

@SpringBootTest(properties =
        "naesan.storage.local.root=build/test-storage/account-deletion")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountDeletionApiIntegrationTest {
    private static final String EMAIL = "delete-account@example.com";
    private static final String RAW_PASSWORD = "password1234";
    private static final byte[] PDF_CONTENT =
            "%PDF-1.7\nreceipt".getBytes(StandardCharsets.UTF_8);

    private final MockMvc mockMvc;
    private final RegisterAccountService registerAccountService;
    private final AuthenticateAccountService authenticateAccountService;
    private final CreateEvidenceDraftService createEvidenceDraftService;
    private final AttachEvidenceFileService attachEvidenceFileService;
    private final TokenSessionManager tokenSessionManager;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AccountDeletionApiIntegrationTest(
            MockMvc mockMvc,
            RegisterAccountService registerAccountService,
            AuthenticateAccountService authenticateAccountService,
            CreateEvidenceDraftService createEvidenceDraftService,
            AttachEvidenceFileService attachEvidenceFileService,
            TokenSessionManager tokenSessionManager,
            JdbcTemplate jdbcTemplate
    ) {
        this.mockMvc = mockMvc;
        this.registerAccountService = registerAccountService;
        this.authenticateAccountService = authenticateAccountService;
        this.createEvidenceDraftService = createEvidenceDraftService;
        this.attachEvidenceFileService = attachEvidenceFileService;
        this.tokenSessionManager = tokenSessionManager;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM evidence_snapshots");
        jdbcTemplate.update("DELETE FROM evidence_files");
        jdbcTemplate.update("DELETE FROM purchase_evidence");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("계정 삭제는 접근을 차단하고 구매 증빙 파일을 삭제한다")
    void deletesAccountEvidenceAndBlocksAccess() throws Exception {
        Account account = registerAccountService.register(EMAIL, RAW_PASSWORD);
        TokenSession tokenSession = tokenSessionManager.start(account);
        var evidence = createEvidenceDraftService.create(
                new CreateEvidenceDraftCommand(
                        account.id(),
                        "생각상점",
                        "생각등대",
                        null,
                        LocalDate.parse("2026-07-01"),
                        new BigDecimal("1000.00"),
                        "KRW"
                )
        );
        attachEvidenceFileService.attach(
                account.id(),
                evidence.id(),
                new ByteArrayInputStream(PDF_CONTENT),
                "application/pdf"
        );
        AuthenticatedAccount principal = new AuthenticatedAccount(
                account.id(),
                EMAIL,
                Instant.now()
        );

        mockMvc.perform(delete("/api/accounts/current")
                        .with(authentication(authenticatedPrincipal(principal)))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(accountStatus(account)).isEqualTo("DELETION_PENDING");
        assertThat(fileState(evidence.id()))
                .isEqualTo(EvidenceFileState.DELETED.name());
        assertThatThrownBy(() ->
                authenticateAccountService.authenticate(EMAIL, RAW_PASSWORD))
                .isInstanceOf(AccountException.class)
                .extracting(exception -> ((AccountException) exception).code())
                .isEqualTo(AccountErrorCode.INVALID_CREDENTIALS);
        assertThatThrownBy(() ->
                tokenSessionManager.refresh(tokenSession.rawRefreshToken()))
                .isInstanceOf(TokenSessionException.class);

        mockMvc.perform(get("/api/evidence")
                        .with(authentication(authenticatedPrincipal(principal))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CSRF token이 없는 계정 삭제 요청은 계정과 파일을 유지한다")
    void rejectsDeletionWithoutCsrfToken() throws Exception {
        Account account = registerAccountService.register(EMAIL, RAW_PASSWORD);
        AuthenticatedAccount principal = new AuthenticatedAccount(
                account.id(),
                EMAIL,
                Instant.now()
        );

        mockMvc.perform(delete("/api/accounts/current")
                        .with(authentication(authenticatedPrincipal(principal))))
                .andExpect(status().isForbidden());

        assertThat(accountStatus(account)).isEqualTo("ACTIVE");
    }

    private UsernamePasswordAuthenticationToken authenticatedPrincipal(
            AuthenticatedAccount account
    ) {
        return UsernamePasswordAuthenticationToken.authenticated(
                account,
                null,
                List.of()
        );
    }

    private String accountStatus(Account account) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM accounts WHERE id = ?",
                String.class,
                account.id()
        );
    }

    private String fileState(java.util.UUID evidenceId) {
        return jdbcTemplate.queryForObject(
                "SELECT state FROM evidence_files WHERE evidence_id = ?",
                String.class,
                evidenceId
        );
    }
}
