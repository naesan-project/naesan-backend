package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.ProofAnchor;
import com.naesan.passport.domain.ProofAnchorState;

@Repository
public class ProofAnchorJdbcRepository implements ProofAnchorRepository {
    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                passport_id,
                schema_version,
                anchor_salt,
                commitment,
                state,
                external_reference,
                created_at,
                updated_at
            FROM proof_anchors
            """;
    private static final String INSERT_PROOF_ANCHOR = """
            INSERT INTO proof_anchors (
                id,
                passport_id,
                schema_version,
                anchor_salt,
                commitment,
                state,
                external_reference,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID = SELECT_COLUMNS + " WHERE id = ?";
    private static final String FIND_BY_PASSPORT_ID =
            SELECT_COLUMNS + " WHERE passport_id = ?";
    private static final String CONFIRM_PREPARED = """
            UPDATE proof_anchors
            SET
                state = ?,
                external_reference = ?,
                updated_at = ?
            WHERE id = ? AND state = 'PREPARED'
            """;

    private final JdbcTemplate jdbcTemplate;

    public ProofAnchorJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(ProofAnchor proofAnchor) {
        jdbcTemplate.update(
                INSERT_PROOF_ANCHOR,
                proofAnchor.id(),
                proofAnchor.passportId(),
                proofAnchor.schemaVersion(),
                proofAnchor.anchorSalt(),
                proofAnchor.commitment(),
                proofAnchor.state().name(),
                proofAnchor.externalReference(),
                proofAnchor.createdAt().atOffset(ZoneOffset.UTC),
                proofAnchor.updatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public Optional<ProofAnchor> findById(UUID proofAnchorId) {
        return findOne(FIND_BY_ID, proofAnchorId);
    }

    private Optional<ProofAnchor> findOne(String sql, UUID id) {
        return jdbcTemplate.query(sql, this::mapProofAnchor, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<ProofAnchor> findByPassportId(UUID passportId) {
        return findOne(FIND_BY_PASSPORT_ID, passportId);
    }

    @Override
    public boolean confirmPrepared(ProofAnchor confirmedProofAnchor) {
        int updatedRowCount = jdbcTemplate.update(
                CONFIRM_PREPARED,
                confirmedProofAnchor.state().name(),
                confirmedProofAnchor.externalReference(),
                confirmedProofAnchor.updatedAt().atOffset(ZoneOffset.UTC),
                confirmedProofAnchor.id()
        );
        return updatedRowCount == 1;
    }

    private ProofAnchor mapProofAnchor(ResultSet resultSet, int rowNumber) throws SQLException {
        return ProofAnchor.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("passport_id", UUID.class),
                resultSet.getInt("schema_version"),
                resultSet.getBytes("anchor_salt"),
                resultSet.getBytes("commitment"),
                ProofAnchorState.valueOf(resultSet.getString("state")),
                resultSet.getString("external_reference"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
