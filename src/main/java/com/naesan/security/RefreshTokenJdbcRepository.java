package com.naesan.security;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenJdbcRepository implements RefreshTokenRepository {
    private static final String INSERT_TOKEN = """
            INSERT INTO refresh_tokens (
                id, account_id, token_hash, issued_at, expires_at,
                consumed_at, revoked_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_HASH_FOR_UPDATE = """
            SELECT id, account_id, token_hash, issued_at, expires_at,
                   consumed_at, revoked_at
            FROM refresh_tokens
            WHERE token_hash = ?
            FOR UPDATE
            """;
    private static final String UPDATE_TOKEN = """
            UPDATE refresh_tokens
            SET consumed_at = ?, revoked_at = ?
            WHERE id = ?
            """;
    private static final String REVOKE_ALL = """
            UPDATE refresh_tokens
            SET revoked_at = ?
            WHERE account_id = ? AND revoked_at IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public RefreshTokenJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(RefreshToken refreshToken) {
        jdbcTemplate.update(
                INSERT_TOKEN,
                refreshToken.id(),
                refreshToken.accountId(),
                refreshToken.tokenHash(),
                toOffsetDateTime(refreshToken.issuedAt()),
                toOffsetDateTime(refreshToken.expiresAt()),
                toOffsetDateTime(refreshToken.consumedAt()),
                toOffsetDateTime(refreshToken.revokedAt())
        );
    }

    @Override
    public Optional<RefreshToken> findByTokenHashForUpdate(byte[] tokenHash) {
        return jdbcTemplate.query(
                        FIND_BY_HASH_FOR_UPDATE,
                        this::mapRefreshToken,
                        tokenHash
                )
                .stream()
                .findFirst();
    }

    private RefreshToken mapRefreshToken(ResultSet resultSet, int rowNumber)
            throws SQLException {
        return RefreshToken.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("account_id", UUID.class),
                resultSet.getBytes("token_hash"),
                resultSet.getObject("issued_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                nullableInstant(resultSet, "consumed_at"),
                nullableInstant(resultSet, "revoked_at")
        );
    }

    private Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        OffsetDateTime value = resultSet.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    @Override
    public void update(RefreshToken refreshToken) {
        jdbcTemplate.update(
                UPDATE_TOKEN,
                toOffsetDateTime(refreshToken.consumedAt()),
                toOffsetDateTime(refreshToken.revokedAt()),
                refreshToken.id()
        );
    }

    @Override
    public void revokeAll(UUID accountId, Instant revokedAt) {
        jdbcTemplate.update(
                REVOKE_ALL,
                toOffsetDateTime(revokedAt),
                accountId
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
