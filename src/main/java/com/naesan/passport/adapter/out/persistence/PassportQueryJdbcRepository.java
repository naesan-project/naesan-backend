package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.PassportDetails;
import com.naesan.passport.application.port.out.PassportQueryRepository;
import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.PassportStatus;
import com.naesan.passport.domain.ProofAnchor;
import com.naesan.passport.domain.ProofAnchorState;

@Repository
public class PassportQueryJdbcRepository implements PassportQueryRepository {
    private static final String SELECT_DETAILS = """
            SELECT
                p.id AS passport_id,
                p.snapshot_id,
                p.current_holder_account_id,
                p.status AS passport_status,
                p.version AS passport_version,
                p.created_at AS passport_created_at,
                pa.id AS proof_anchor_id,
                pa.schema_version AS proof_schema_version,
                pa.anchor_salt,
                pa.commitment,
                pa.state AS proof_state,
                pa.external_reference,
                pa.created_at AS proof_created_at,
                pa.updated_at AS proof_updated_at,
                pe.product_name,
                pe.merchant_name,
                pe.purchased_at
            FROM passports p
            JOIN proof_anchors pa ON pa.passport_id = p.id
            JOIN evidence_snapshots es ON es.id = p.snapshot_id
            JOIN purchase_evidence pe ON pe.id = es.evidence_id
            """;
    private static final String FIND_ALL_BY_HOLDER_ACCOUNT_ID = SELECT_DETAILS + """
             WHERE p.current_holder_account_id = ?
             ORDER BY p.created_at DESC, p.id DESC
            """;
    private static final String FIND_BY_ID_AND_HOLDER_ACCOUNT_ID = SELECT_DETAILS + """
             WHERE p.id = ? AND p.current_holder_account_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PassportQueryJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<PassportDetails> findAllByHolderAccountId(UUID accountId) {
        return jdbcTemplate.query(
                FIND_ALL_BY_HOLDER_ACCOUNT_ID,
                this::mapDetails,
                accountId
        );
    }

    @Override
    public Optional<PassportDetails> findByIdAndHolderAccountId(
            UUID passportId,
            UUID accountId
    ) {
        return jdbcTemplate.query(
                        FIND_BY_ID_AND_HOLDER_ACCOUNT_ID,
                        this::mapDetails,
                        passportId,
                        accountId
                )
                .stream()
                .findFirst();
    }

    private PassportDetails mapDetails(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        Passport passport = Passport.restore(
                resultSet.getObject("passport_id", UUID.class),
                resultSet.getObject("snapshot_id", UUID.class),
                resultSet.getObject("current_holder_account_id", UUID.class),
                PassportStatus.valueOf(resultSet.getString("passport_status")),
                resultSet.getLong("passport_version"),
                resultSet.getObject(
                        "passport_created_at",
                        OffsetDateTime.class
                ).toInstant()
        );
        ProofAnchor proofAnchor = ProofAnchor.restore(
                resultSet.getObject("proof_anchor_id", UUID.class),
                passport.id(),
                resultSet.getInt("proof_schema_version"),
                resultSet.getBytes("anchor_salt"),
                resultSet.getBytes("commitment"),
                ProofAnchorState.valueOf(resultSet.getString("proof_state")),
                resultSet.getString("external_reference"),
                resultSet.getObject("proof_created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("proof_updated_at", OffsetDateTime.class).toInstant()
        );
        return new PassportDetails(
                passport,
                proofAnchor,
                resultSet.getString("product_name"),
                resultSet.getString("merchant_name"),
                resultSet.getObject("purchased_at", LocalDate.class)
        );
    }
}
