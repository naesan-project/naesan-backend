package com.naesan.evidence.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.domain.EvidenceSnapshot;

@Repository
public class EvidenceSnapshotJdbcRepository implements EvidenceSnapshotRepository {
    private static final String INSERT_SNAPSHOT = """
            INSERT INTO evidence_snapshots (
                id,
                evidence_id,
                schema_version,
                canonical_payload,
                snapshot_digest,
                created_at
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_EVIDENCE_ID = """
            SELECT
                id,
                evidence_id,
                schema_version,
                canonical_payload,
                snapshot_digest,
                created_at
            FROM evidence_snapshots
            WHERE evidence_id = ?
            """;
    private static final String FIND_BY_ID = """
            SELECT
                id,
                evidence_id,
                schema_version,
                canonical_payload,
                snapshot_digest,
                created_at
            FROM evidence_snapshots
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public EvidenceSnapshotJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(EvidenceSnapshot snapshot) {
        jdbcTemplate.update(
                INSERT_SNAPSHOT,
                snapshot.id(),
                snapshot.evidenceId(),
                snapshot.schemaVersion(),
                snapshot.canonicalPayload(),
                snapshot.snapshotDigest(),
                snapshot.createdAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public Optional<EvidenceSnapshot> findById(UUID snapshotId) {
        return findOne(FIND_BY_ID, snapshotId);
    }

    private Optional<EvidenceSnapshot> findOne(String sql, UUID id) {
        return jdbcTemplate.query(sql, this::mapSnapshot, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<EvidenceSnapshot> findByEvidenceId(UUID evidenceId) {
        return findOne(FIND_BY_EVIDENCE_ID, evidenceId);
    }

    private EvidenceSnapshot mapSnapshot(ResultSet resultSet, int rowNumber) throws SQLException {
        return new EvidenceSnapshot(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("evidence_id", UUID.class),
                resultSet.getInt("schema_version"),
                resultSet.getBytes("canonical_payload"),
                resultSet.getString("snapshot_digest"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
