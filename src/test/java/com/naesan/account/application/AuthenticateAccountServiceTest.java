package com.naesan.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.AccountStatus;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;

class AuthenticateAccountServiceTest {
    private static final String RAW_PASSWORD = "password1234";
    private static final PasswordHash PASSWORD_HASH =
            new PasswordHash("$2b$12$" + "a".repeat(53));
    private static final Instant CREATED_AT = Instant.parse("2026-07-17T00:00:00Z");

    @Test
    @DisplayName("정규화한 이메일과 올바른 비밀번호로 ACTIVE 계정을 인증한다")
    void authenticatesActiveAccount() {
        Account account = account(AccountStatus.ACTIVE);
        FakeAccountRepository accountRepository = new FakeAccountRepository(account);
        AuthenticateAccountService service = new AuthenticateAccountService(
                accountRepository,
                new FakePasswordHasher()
        );

        Account authenticatedAccount = service.authenticate(
                "  User@Example.COM  ",
                RAW_PASSWORD
        );

        assertThat(authenticatedAccount).isEqualTo(account);
    }

    @Test
    @DisplayName("존재하지 않는 이메일은 자격증명 오류로 거절한다")
    void rejectsUnknownEmail() {
        AuthenticateAccountService service = new AuthenticateAccountService(
                new FakeAccountRepository(null),
                new FakePasswordHasher()
        );

        assertInvalidCredentials(() -> service.authenticate("unknown@example.com", RAW_PASSWORD));
    }

    @Test
    @DisplayName("틀린 비밀번호는 자격증명 오류로 거절한다")
    void rejectsWrongPassword() {
        AuthenticateAccountService service = new AuthenticateAccountService(
                new FakeAccountRepository(account(AccountStatus.ACTIVE)),
                new FakePasswordHasher()
        );

        assertInvalidCredentials(() -> service.authenticate(
                "user@example.com",
                "different-password"
        ));
    }

    @Test
    @DisplayName("비활성 계정은 올바른 비밀번호여도 자격증명 오류로 거절한다")
    void rejectsInactiveAccount() {
        AuthenticateAccountService service = new AuthenticateAccountService(
                new FakeAccountRepository(account(AccountStatus.DELETION_PENDING)),
                new FakePasswordHasher()
        );

        assertInvalidCredentials(() -> service.authenticate("user@example.com", RAW_PASSWORD));
    }

    private void assertInvalidCredentials(Runnable authentication) {
        assertThatThrownBy(authentication::run)
                .isInstanceOfSatisfying(
                        AccountException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AccountErrorCode.INVALID_CREDENTIALS)
                );
    }

    private Account account(AccountStatus status) {
        return Account.restore(
                UUID.randomUUID(),
                new Email("user@example.com"),
                PASSWORD_HASH,
                status,
                CREATED_AT
        );
    }

    private static final class FakeAccountRepository implements AccountRepository {
        private final Account account;

        private FakeAccountRepository(Account account) {
            this.account = account;
        }

        @Override
        public boolean existsByEmail(Email email) {
            return false;
        }

        @Override
        public Optional<Account> findByEmail(Email email) {
            return Optional.ofNullable(account)
                    .filter(foundAccount -> foundAccount.email().equals(email));
        }

        @Override
        public void save(Account account) {
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        @Override
        public PasswordHash hash(String rawPassword) {
            return PASSWORD_HASH;
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash passwordHash) {
            return RAW_PASSWORD.equals(rawPassword) && PASSWORD_HASH.equals(passwordHash);
        }
    }
}
