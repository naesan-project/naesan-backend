package com.naesan.share.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.domain.PassportStatus;
import com.naesan.passport.domain.ProofAnchorState;
import com.naesan.share.application.PublicShareVerificationSource;
import com.naesan.share.application.port.out.PublicShareVerificationRepository;
import com.naesan.share.domain.PublicShare;
import com.naesan.share.domain.PublicShareCapability;

@Repository
public class PublicShareVerificationJdbcRepository
        implements PublicShareVerificationRepository {
    private static final String FIND_BY_TOKEN_HASH = """
            SELECT
                ps.id AS share_id,
                ps.passport_id,
                ps.token_hash,
                ps.capability,
                ps.expires_at,
                ps.revoked_at,
                ps.created_at AS share_created_at,
                pe.product_name,
                pe.purchased_at,
                p.status AS passport_status,
                pa.state AS proof_state,
                pa.commitment,
                pa.schema_version AS commitment_schema_version,
                es.snapshot_digest,
                pa.anchor_salt,
                es.schema_version AS snapshot_schema_version,
                es.canonical_payload
            FROM public_shares ps
            JOIN passports p ON p.id = ps.passport_id
            JOIN proof_anchors pa ON pa.passport_id = p.id
            JOIN evidence_snapshots es ON es.id = p.snapshot_id
            JOIN purchase_evidence pe ON pe.id = es.evidence_id
            WHERE ps.token_hash = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PublicShareVerificationJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<PublicShareVerificationSource> findByTokenHash(byte[] tokenHash) {
        return jdbcTemplate.query(FIND_BY_TOKEN_HASH, this::mapSource, tokenHash)
                .stream()
                .findFirst();
    }

    private PublicShareVerificationSource mapSource(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        return new PublicShareVerificationSource(
                mapShare(resultSet),
                resultSet.getString("product_name"),
                resultSet.getObject("purchased_at", java.time.LocalDate.class),
                PassportStatus.valueOf(resultSet.getString("passport_status")),
                ProofAnchorState.valueOf(resultSet.getString("proof_state")),
                resultSet.getBytes("commitment"),
                resultSet.getInt("commitment_schema_version"),
                resultSet.getString("snapshot_digest"),
                resultSet.getBytes("anchor_salt"),
                resultSet.getInt("snapshot_schema_version"),
                resultSet.getBytes("canonical_payload")
        );
    }

    private PublicShare mapShare(ResultSet resultSet) throws SQLException {
        OffsetDateTime revokedAt = resultSet.getObject(
                "revoked_at",
                OffsetDateTime.class
        );
        return PublicShare.restore(
                resultSet.getObject("share_id", java.util.UUID.class),
                resultSet.getObject("passport_id", java.util.UUID.class),
                resultSet.getBytes("token_hash"),
                PublicShareCapability.valueOf(resultSet.getString("capability")),
                resultSet.getObject("expires_at", OffsetDateTime.class).toInstant(),
                revokedAt == null ? null : revokedAt.toInstant(),
                resultSet.getObject("share_created_at", OffsetDateTime.class).toInstant()
        );
    }
}
