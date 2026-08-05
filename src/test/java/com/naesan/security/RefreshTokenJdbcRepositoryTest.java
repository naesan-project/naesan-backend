package com.naesan.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.TestcontainersConfiguration;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class RefreshTokenJdbcRepositoryTest {
    private static final Instant ISSUED_AT = Instant.parse("2026-08-06T00:00:00Z");

    private final RefreshTokenRepository refreshTokenRepository;
    private final RegisterAccountService registerAccountService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    RefreshTokenJdbcRepositoryTest(
            RefreshTokenRepository refreshTokenRepository,
            RegisterAccountService registerAccountService,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.registerAccountService = registerAccountService;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM accounts");
    }

    @Test
    @DisplayName("Refresh token을 hash로 조회하고 사용 시각을 저장한다")
    void savesFindsAndConsumesRefreshToken() {
        Account account = registerAccountService.register(
                "refresh@example.com",
                "password1234"
        );
        RefreshToken token = token(account.id(), new byte[32]);
        refreshTokenRepository.save(token);

        RefreshToken consumed = transactionTemplate.execute(status -> {
            RefreshToken locked = refreshTokenRepository
                    .findByTokenHashForUpdate(token.tokenHash())
                    .orElseThrow();
            RefreshToken updated = locked.consume(ISSUED_AT.plusSeconds(60));
            refreshTokenRepository.update(updated);
            return updated;
        });

        assertThat(consumed).isNotNull();
        Instant persistedConsumedAt = transactionTemplate.execute(status -> refreshTokenRepository
                .findByTokenHashForUpdate(token.tokenHash())
                .orElseThrow()
                .consumedAt());
        assertThat(persistedConsumedAt).isEqualTo(ISSUED_AT.plusSeconds(60));
    }

    @Test
    @DisplayName("계정의 모든 refresh token을 한 번에 폐기한다")
    void revokesAllRefreshTokensForAccount() {
        Account account = registerAccountService.register(
                "revoke@example.com",
                "password1234"
        );
        RefreshToken first = token(account.id(), new byte[32]);
        byte[] secondHash = new byte[32];
        secondHash[0] = 1;
        RefreshToken second = token(account.id(), secondHash);
        refreshTokenRepository.save(first);
        refreshTokenRepository.save(second);

        refreshTokenRepository.revokeAll(account.id(), ISSUED_AT.plusSeconds(30));

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE account_id = ? AND revoked_at IS NULL",
                Integer.class,
                account.id()
        );
        assertThat(activeCount).isZero();
    }

    @Test
    @DisplayName("32 byte가 아닌 refresh token hash는 DB에 저장할 수 없다")
    void rejectsInvalidTokenHashLengthAtDatabaseBoundary() {
        Account account = registerAccountService.register(
                "constraint@example.com",
                "password1234"
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO refresh_tokens (
                    id, account_id, token_hash, issued_at, expires_at
                ) VALUES (?, ?, ?, ?, ?)
                """,
                UUID.randomUUID(),
                account.id(),
                new byte[31],
                ISSUED_AT.atOffset(ZoneOffset.UTC),
                ISSUED_AT.plusSeconds(3_600).atOffset(ZoneOffset.UTC)
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    private RefreshToken token(UUID accountId, byte[] tokenHash) {
        return RefreshToken.issue(
                UUID.randomUUID(),
                accountId,
                tokenHash,
                ISSUED_AT,
                ISSUED_AT.plusSeconds(3_600)
        );
    }
}
