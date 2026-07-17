package com.naesan.account.adapter.in;

import static com.naesan.account.application.AccountErrorCode.EMAIL_ALREADY_REGISTERED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.AccountException;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;
import com.naesan.account.domain.PasswordHash;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RegisterAccountIntegrationTest {
    private static final String RAW_PASSWORD = "password1234";
    private static final String EMAIL = "user@example.com";
    private static final String CONCURRENT_EMAIL = "concurrent@example.com";
    private static final Instant CREATED_AT = Instant.parse("2026-07-16T00:00:00Z");

    private final RegisterAccountService registerAccountService;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Autowired
    RegisterAccountIntegrationTest(
            RegisterAccountService registerAccountService,
            AccountRepository accountRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbcTemplate
    ) {
        this.registerAccountService = registerAccountService;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbcTemplate = jdbcTemplate;
    }

    @BeforeEach
    void deleteAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("회원가입 입력을 BCrypt로 해시해 PostgreSQL에 저장한다")
    void registersAccountInPostgreSql() {
        Account account = registerAccountService.register("  User@Example.COM  ", RAW_PASSWORD);

        Map<String, Object> savedAccount = jdbcTemplate.queryForMap(
                "SELECT id, email, password_hash, status FROM accounts WHERE id = ?",
                account.id()
        );
        String passwordHash = (String) savedAccount.get("password_hash");
        Boolean createdAtMatches = jdbcTemplate.queryForObject(
                "SELECT created_at = ? FROM accounts WHERE id = ?",
                Boolean.class,
                account.createdAt().atOffset(ZoneOffset.UTC),
                account.id()
        );

        assertThat(savedAccount)
                .containsEntry("id", account.id())
                .containsEntry("email", EMAIL)
                .containsEntry("status", "ACTIVE");
        assertThat(passwordHash).startsWith("$2").contains("$12$");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, passwordHash)).isTrue();
        assertThat(createdAtMatches).isTrue();
    }

    @Test
    @DisplayName("정규화된 이메일의 순차 중복 회원가입을 계정 오류로 거절한다")
    void rejectsRegisteredEmail() {
        registerAccountService.register(EMAIL, RAW_PASSWORD);

        assertThatThrownBy(() -> registerAccountService.register("User@Example.com", RAW_PASSWORD))
                .isInstanceOfSatisfying(
                        AccountException.class,
                        exception -> assertThat(exception.code()).isEqualTo(EMAIL_ALREADY_REGISTERED)
                );
        assertThat(countAccounts(EMAIL)).isOne();
    }

    @Test
    @DisplayName("동일 이메일의 동시 insert는 한 건만 저장한다")
    void savesOnlyOneConcurrentDuplicate() throws Exception {
        Account firstAccount = account(CONCURRENT_EMAIL);
        Account secondAccount = account(CONCURRENT_EMAIL);
        CyclicBarrier barrier = new CyclicBarrier(2);

        try (ExecutorService executorService = Executors.newFixedThreadPool(2)) {
            Future<SaveOutcome> firstOutcome = executorService.submit(
                    () -> saveAfterBarrier(firstAccount, barrier)
            );
            Future<SaveOutcome> secondOutcome = executorService.submit(
                    () -> saveAfterBarrier(secondAccount, barrier)
            );

            assertThat(List.of(firstOutcome.get(), secondOutcome.get()))
                    .containsExactlyInAnyOrder(SaveOutcome.SUCCESS, SaveOutcome.DUPLICATE);
        }

        assertThat(countAccounts(CONCURRENT_EMAIL)).isOne();
    }

    private Account account(String email) {
        return Account.create(
                UUID.randomUUID(),
                new Email(email),
                new PasswordHash(passwordEncoder.encode(RAW_PASSWORD)),
                CREATED_AT
        );
    }

    private SaveOutcome saveAfterBarrier(Account account, CyclicBarrier barrier) throws Exception {
        barrier.await();

        try {
            accountRepository.save(account);
            return SaveOutcome.SUCCESS;
        } catch (AccountException exception) {
            if (exception.code() == EMAIL_ALREADY_REGISTERED) {
                return SaveOutcome.DUPLICATE;
            }

            throw exception;
        }
    }

    private int countAccounts(String email) {
        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ?",
                Integer.class,
                email
        );
        return accountCount == null ? 0 : accountCount;
    }

    private enum SaveOutcome {
        SUCCESS,
        DUPLICATE
    }
}
