package com.naesan.account.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.naesan.TestcontainersConfiguration;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountSchemaTest {
    private static final String VALID_EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "password1234";
    private static final String ACTIVE_STATUS = "ACTIVE";
    private static final String INSERT_ACCOUNT = """
            INSERT INTO accounts (id, email, password_hash, status)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    AccountSchemaTest(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @BeforeEach
    void deleteAccounts() {
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("정규화된 이메일과 BCrypt hash를 가진 계정을 저장한다")
    void insertsValidAccount() {
        String passwordHash = validPasswordHash();

        insertAccount(VALID_EMAIL, passwordHash, ACTIVE_STATUS);

        Map<String, Object> account = jdbcTemplate.queryForMap(
                "SELECT email, password_hash, status, created_at FROM accounts WHERE email = ?",
                VALID_EMAIL
        );
        assertThat(account)
                .containsEntry("email", VALID_EMAIL)
                .containsEntry("password_hash", passwordHash)
                .containsEntry("status", ACTIVE_STATUS);
        assertThat(account.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("같은 정규화 이메일을 가진 계정을 두 번 저장할 수 없다")
    void rejectsDuplicateEmail() {
        String passwordHash = validPasswordHash();
        insertAccount(VALID_EMAIL, passwordHash, ACTIVE_STATUS);

        assertThatThrownBy(() -> insertAccount(VALID_EMAIL, passwordHash, ACTIVE_STATUS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "User@example.com",
            "user name@example.com",
            "사용자@example.com",
            "user@@example.com"
    })
    @DisplayName("정규화되지 않았거나 구조가 잘못된 이메일을 저장할 수 없다")
    void rejectsInvalidEmail(String email) {
        String passwordHash = validPasswordHash();

        assertThatThrownBy(() -> insertAccount(email, passwordHash, ACTIVE_STATUS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("254 byte 이메일을 저장한다")
    void insertsEmailWith254Bytes() {
        String email = "a".repeat(242) + "@example.com";
        String passwordHash = validPasswordHash();

        insertAccount(email, passwordHash, ACTIVE_STATUS);

        Integer accountCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM accounts WHERE email = ?",
                Integer.class,
                email
        );
        assertThat(accountCount).isOne();
    }

    @Test
    @DisplayName("254 byte를 초과하는 이메일을 저장할 수 없다")
    void rejectsEmailLongerThan254Bytes() {
        String email = "a".repeat(243) + "@example.com";
        String passwordHash = validPasswordHash();

        assertThatThrownBy(() -> insertAccount(email, passwordHash, ACTIVE_STATUS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("평문 비밀번호를 password_hash에 저장할 수 없다")
    void rejectsPlaintextPassword() {
        assertThatThrownBy(() -> insertAccount(VALID_EMAIL, RAW_PASSWORD, ACTIVE_STATUS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("BCrypt cost 12가 아닌 hash를 저장할 수 없다")
    void rejectsBcryptHashWithDifferentCost() {
        String passwordHash = validPasswordHash().replace("$12$", "$10$");

        assertThatThrownBy(() -> insertAccount(VALID_EMAIL, passwordHash, ACTIVE_STATUS))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("정의되지 않은 계정 상태를 저장할 수 없다")
    void rejectsInvalidStatus() {
        String passwordHash = validPasswordHash();

        assertThatThrownBy(() -> insertAccount(VALID_EMAIL, passwordHash, "SUSPENDED"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String validPasswordHash() {
        return passwordEncoder.encode(RAW_PASSWORD);
    }

    private void insertAccount(String email, String passwordHash, String status) {
        jdbcTemplate.update(INSERT_ACCOUNT, UUID.randomUUID(), email, passwordHash, status);
    }
}
