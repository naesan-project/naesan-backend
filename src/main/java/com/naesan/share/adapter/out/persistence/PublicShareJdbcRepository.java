package com.naesan.share.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.share.domain.PublicShare;
import com.naesan.share.domain.PublicShareCapability;

@Repository
public class PublicShareJdbcRepository implements PublicShareRepository {
    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                passport_id,
                token_hash,
                capability,
                expires_at,
                revoked_at,
                created_at
            FROM public_shares
            """;
    private static final String INSERT_SHARE = """
            INSERT INTO public_shares (
                id,
                passport_id,
                token_hash,
                capability,
                expires_at,
                revoked_at,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String UPDATE_SHARE = """
            UPDATE public_shares
            SET revoked_at = ?
            WHERE id = ?
            """;
    private static final String REVOKE_ALL_BY_PASSPORT_ID = """
            UPDATE public_shares
            SET revoked_at = ?
            WHERE passport_id = ? AND revoked_at IS NULL
            """;
    private static final String FIND_BY_ID_AND_PASSPORT_ID = SELECT_COLUMNS + """
             WHERE id = ? AND passport_id = ?
            """;
    private static final String FIND_UNREVOKED_BY_PASSPORT_ID = SELECT_COLUMNS + """
             WHERE passport_id = ? AND revoked_at IS NULL
            """;
    private static final String FIND_BY_TOKEN_HASH = SELECT_COLUMNS + """
             WHERE token_hash = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PublicShareJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(PublicShare publicShare) {
        jdbcTemplate.update(
                INSERT_SHARE,
                publicShare.id(),
                publicShare.passportId(),
                publicShare.tokenHash(),
                publicShare.capability().name(),
                publicShare.expiresAt().atOffset(ZoneOffset.UTC),
                toOffsetDateTime(publicShare.revokedAt()),
                publicShare.createdAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public void update(PublicShare publicShare) {
        jdbcTemplate.update(
                UPDATE_SHARE,
                toOffsetDateTime(publicShare.revokedAt()),
                publicShare.id()
        );
    }

    @Override
    public int revokeAllByPassportId(UUID passportId, Instant revokedAt) {
        return jdbcTemplate.update(
                REVOKE_ALL_BY_PASSPORT_ID,
                revokedAt.atOffset(ZoneOffset.UTC),
                passportId
        );
    }

    @Override
    public Optional<PublicShare> findByIdAndPassportId(
            UUID shareId,
            UUID passportId
    ) {
        return findOne(FIND_BY_ID_AND_PASSPORT_ID, shareId, passportId);
    }

    @Override
    public Optional<PublicShare> findUnrevokedByPassportId(UUID passportId) {
        return findOne(FIND_UNREVOKED_BY_PASSPORT_ID, passportId);
    }

    @Override
    public Optional<PublicShare> findByTokenHash(byte[] tokenHash) {
        return findOne(FIND_BY_TOKEN_HASH, tokenHash);
    }

    private Optional<PublicShare> findOne(String sql, Object... arguments) {
        return jdbcTemplate.query(sql, this::mapShare, arguments)
                .stream()
                .findFirst();
    }

    private PublicShare mapShare(ResultSet resultSet, int rowNumber) throws SQLException {
        OffsetDateTime revokedAt = resultSet.getObject(
                "revoked_at",
                OffsetDateTime.class
        );
        return PublicShare.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("passport_id", UUID.class),
                resultSet.getBytes("token_hash"),
                PublicShareCapability.valueOf(resultSet.getString("capability")),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }
}
