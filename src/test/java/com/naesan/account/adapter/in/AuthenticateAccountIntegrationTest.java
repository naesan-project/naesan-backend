package com.naesan.account.adapter.in;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.AccountErrorCode;
import com.naesan.account.application.AccountException;
import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.AccountStatus;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AuthenticateAccountIntegrationTest {
    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password1234";

    private final AuthenticateAccountService authenticateAccountService;
    private final RegisterAccountService registerAccountService;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    AuthenticateAccountIntegrationTest(
            AuthenticateAccountService authenticateAccountService,
            RegisterAccountService registerAccountService,
            JdbcTemplate jdbcTemplate
    ) {
        this.authenticateAccountService = authenticateAccountService;
        this.registerAccountService = registerAccountService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void deleteAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("PostgreSQL 계정과 BCrypt hash로 자격증명을 검증한다")
    void authenticatesPersistedAccount() {
        Account registeredAccount = registerAccountService.register(EMAIL, RAW_PASSWORD);

        Account authenticatedAccount = authenticateAccountService.authenticate(
                "  User@Example.COM  ",
                RAW_PASSWORD
        );

        assertThat(authenticatedAccount).isEqualTo(registeredAccount);
        assertThat(authenticatedAccount.email().value()).isEqualTo(EMAIL);
        assertThat(authenticatedAccount.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(authenticatedAccount.createdAt()).isEqualTo(registeredAccount.createdAt());
    }

    @Test
    @DisplayName("PostgreSQL 계정의 틀린 비밀번호를 자격증명 오류로 거절한다")
    void rejectsWrongPassword() {
        registerAccountService.register(EMAIL, RAW_PASSWORD);

        assertThatThrownBy(() -> authenticateAccountService.authenticate(
                EMAIL,
                "different-password"
        )).isInstanceOfSatisfying(
                AccountException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(AccountErrorCode.INVALID_CREDENTIALS)
        );
    }

    @Test
    @DisplayName("PostgreSQL의 비활성 계정을 인증하지 않는다")
    void rejectsInactiveAccount() {
        registerAccountService.register(EMAIL, RAW_PASSWORD);
        jdbcTemplate.update(
                "UPDATE accounts SET status = ? WHERE email = ?",
                AccountStatus.DELETION_PENDING.name(),
                EMAIL
        );

        assertThatThrownBy(() -> authenticateAccountService.authenticate(
                EMAIL,
                RAW_PASSWORD
        )).isInstanceOfSatisfying(
                AccountException.class,
                exception -> assertThat(exception.code())
                        .isEqualTo(AccountErrorCode.INVALID_CREDENTIALS)
        );
    }
}
