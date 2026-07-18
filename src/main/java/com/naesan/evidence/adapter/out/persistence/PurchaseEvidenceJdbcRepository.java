package com.naesan.evidence.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.evidence.application.EvidenceException;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.evidence.domain.EvidenceMetadata;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.PurchaseEvidenceState;

@Repository
public class PurchaseEvidenceJdbcRepository implements PurchaseEvidenceRepository {
    private static final String INSERT_EVIDENCE = """
            INSERT INTO purchase_evidence (
                id,
                owner_account_id,
                merchant_name,
                product_name,
                serial_number,
                purchased_at,
                amount,
                currency,
                state,
                version,
                created_at,
                updated_at,
                confirmed_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID = """
            SELECT
                id,
                owner_account_id,
                merchant_name,
                product_name,
                serial_number,
                purchased_at,
                amount,
                currency,
                state,
                version,
                created_at,
                updated_at,
                confirmed_at
            FROM purchase_evidence
            WHERE id = ?
            """;
    private static final String UPDATE_EVIDENCE = """
            UPDATE purchase_evidence
            SET
                merchant_name = ?,
                product_name = ?,
                serial_number = ?,
                purchased_at = ?,
                amount = ?,
                currency = ?,
                state = ?,
                version = ?,
                updated_at = ?,
                confirmed_at = ?
            WHERE id = ? AND version = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PurchaseEvidenceJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(PurchaseEvidence evidence) {
        EvidenceMetadata metadata = evidence.metadata();
        jdbcTemplate.update(
                INSERT_EVIDENCE,
                evidence.id(),
                evidence.ownerAccountId(),
                metadata.merchantName(),
                metadata.productName(),
                metadata.serialNumber(),
                metadata.purchasedAt(),
                metadata.amount(),
                metadata.currency(),
                evidence.state().name(),
                evidence.version(),
                evidence.createdAt().atOffset(ZoneOffset.UTC),
                evidence.updatedAt().atOffset(ZoneOffset.UTC),
                toOffsetDateTime(evidence.confirmedAt())
        );
    }

    private OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    @Override
    public void update(PurchaseEvidence evidence) {
        EvidenceMetadata metadata = evidence.metadata();
        int updatedRowCount = jdbcTemplate.update(
                UPDATE_EVIDENCE,
                metadata.merchantName(),
                metadata.productName(),
                metadata.serialNumber(),
                metadata.purchasedAt(),
                metadata.amount(),
                metadata.currency(),
                evidence.state().name(),
                evidence.version(),
                evidence.updatedAt().atOffset(ZoneOffset.UTC),
                toOffsetDateTime(evidence.confirmedAt()),
                evidence.id(),
                evidence.version() - 1
        );
        if (updatedRowCount == 0) {
            throw EvidenceException.concurrentModification();
        }
    }

    @Override
    public Optional<PurchaseEvidence> findById(UUID evidenceId) {
        return jdbcTemplate.query(FIND_BY_ID, this::mapEvidence, evidenceId)
                .stream()
                .findFirst();
    }

    private PurchaseEvidence mapEvidence(ResultSet resultSet, int rowNumber) throws SQLException {
        EvidenceMetadata metadata = new EvidenceMetadata(
                resultSet.getString("merchant_name"),
                resultSet.getString("product_name"),
                resultSet.getString("serial_number"),
                resultSet.getObject("purchased_at", LocalDate.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency")
        );
        OffsetDateTime confirmedAt = resultSet.getObject(
                "confirmed_at",
                OffsetDateTime.class
        );

        return PurchaseEvidence.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("owner_account_id", UUID.class),
                metadata,
                PurchaseEvidenceState.valueOf(resultSet.getString("state")),
                resultSet.getLong("version"),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant(),
                confirmedAt == null ? null : confirmedAt.toInstant()
        );
    }
}
