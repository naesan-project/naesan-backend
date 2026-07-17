package com.naesan.account.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.application.port.out.PasswordHasher;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.AccountStatus;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;

class RegisterAccountServiceTest {
    private static final String RAW_PASSWORD = "password1234";
    private static final PasswordHash PASSWORD_HASH =
            new PasswordHash("$2b$12$" + "a".repeat(53));
    private static final Instant CREATED_AT = Instant.parse("2026-07-15T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(CREATED_AT, ZoneOffset.UTC);

    @Test
    @DisplayName("입력을 검증하고 해시한 ACTIVE 계정을 저장한다")
    void registersAccount() {
        FakeAccountRepository accountRepository = new FakeAccountRepository();
        FakePasswordHasher passwordHasher = new FakePasswordHasher();
        RegisterAccountService registerAccountService = new RegisterAccountService(
                accountRepository,
                passwordHasher,
                CLOCK
        );

        Account account = registerAccountService.register("  User@Example.COM  ", RAW_PASSWORD);

        assertThat(account.id()).isNotNull();
        assertThat(account.email()).isEqualTo(new Email("user@example.com"));
        assertThat(account.passwordHash()).isEqualTo(PASSWORD_HASH);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.createdAt()).isEqualTo(CREATED_AT);
        assertThat(accountRepository.savedAccount()).isEqualTo(account);
        assertThat(passwordHasher.rawPassword()).isEqualTo(RAW_PASSWORD);
    }

    @Test
    @DisplayName("정규화된 이메일이 중복되면 계정 오류를 반환하고 저장하지 않는다")
    void rejectsRegisteredEmail() {
        FakeAccountRepository accountRepository = new FakeAccountRepository();
        accountRepository.addExistingEmail(new Email("user@example.com"));
        RegisterAccountService registerAccountService = new RegisterAccountService(
                accountRepository,
                new FakePasswordHasher(),
                CLOCK
        );

        assertThatThrownBy(() -> registerAccountService.register("User@Example.com", RAW_PASSWORD))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo(AccountErrorCode.EMAIL_ALREADY_REGISTERED)
                );
        assertThat(accountRepository.savedAccount()).isNull();
    }

    @Test
    @DisplayName("유효하지 않은 비밀번호는 계정으로 저장하지 않는다")
    void rejectsInvalidPassword() {
        FakeAccountRepository accountRepository = new FakeAccountRepository();
        RegisterAccountService registerAccountService = new RegisterAccountService(
                accountRepository,
                new FakePasswordHasher(),
                CLOCK
        );

        assertThatThrownBy(() -> registerAccountService.register("user@example.com", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accountRepository.savedAccount()).isNull();
    }

    private static final class FakeAccountRepository implements AccountRepository {
        private Email existingEmail;
        private Account savedAccount;

        @Override
        public boolean existsByEmail(Email email) {
            return email.equals(existingEmail);
        }

        @Override
        public Optional<Account> findByEmail(Email email) {
            return Optional.empty();
        }

        @Override
        public void save(Account account) {
            savedAccount = account;
        }

        void addExistingEmail(Email email) {
            existingEmail = email;
        }

        Account savedAccount() {
            return savedAccount;
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {
        private String rawPassword;

        @Override
        public PasswordHash hash(String rawPassword) {
            this.rawPassword = rawPassword;
            return PASSWORD_HASH;
        }

        @Override
        public boolean matches(String rawPassword, PasswordHash passwordHash) {
            return false;
        }

        String rawPassword() {
            return rawPassword;
        }
    }
}
