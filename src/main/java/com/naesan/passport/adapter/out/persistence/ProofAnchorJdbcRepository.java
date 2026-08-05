package com.naesan.passport.adapter.out.persistence;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.EvmAnchorEvidence;
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
                chain_id,
                contract_address,
                transaction_hash,
                block_number,
                block_hash,
                confirmation_count,
                read_back_commitment,
                chain_checked_at,
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
                chain_id,
                contract_address,
                transaction_hash,
                block_number,
                block_hash,
                confirmation_count,
                read_back_commitment,
                chain_checked_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID = SELECT_COLUMNS + " WHERE id = ?";
    private static final String FIND_BY_PASSPORT_ID =
            SELECT_COLUMNS + " WHERE passport_id = ?";
    private static final String CONFIRM_PREPARED = """
            UPDATE proof_anchors
            SET
                state = ?,
                external_reference = ?,
                chain_id = ?,
                contract_address = ?,
                transaction_hash = ?,
                block_number = ?,
                block_hash = ?,
                confirmation_count = ?,
                read_back_commitment = ?,
                chain_checked_at = ?,
                updated_at = ?
            WHERE id = ? AND state = 'PREPARED'
            """;
    private static final String MARK_RECONCILE_PENDING = """
            UPDATE proof_anchors
            SET
                state = ?,
                external_reference = ?,
                updated_at = ?
            WHERE id = ? AND state = 'PREPARED'
            """;
    private static final String UPDATE_RECONCILE_PENDING = """
            UPDATE proof_anchors
            SET
                state = ?,
                external_reference = ?,
                chain_id = ?,
                contract_address = ?,
                transaction_hash = ?,
                block_number = ?,
                block_hash = ?,
                confirmation_count = ?,
                read_back_commitment = ?,
                chain_checked_at = ?,
                updated_at = ?
            WHERE id = ? AND state = 'RECONCILE_PENDING'
            """;
    private static final String RESUME_RECONCILIATION = """
            UPDATE proof_anchors
            SET
                state = ?,
                updated_at = ?
            WHERE id = ? AND state = 'MANUAL_REVIEW'
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
                chainId(proofAnchor),
                contractAddress(proofAnchor),
                transactionHash(proofAnchor),
                blockNumber(proofAnchor),
                blockHash(proofAnchor),
                confirmationCount(proofAnchor),
                readBackCommitment(proofAnchor),
                chainCheckedAt(proofAnchor),
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
                chainId(confirmedProofAnchor),
                contractAddress(confirmedProofAnchor),
                transactionHash(confirmedProofAnchor),
                blockNumber(confirmedProofAnchor),
                blockHash(confirmedProofAnchor),
                confirmationCount(confirmedProofAnchor),
                readBackCommitment(confirmedProofAnchor),
                chainCheckedAt(confirmedProofAnchor),
                confirmedProofAnchor.updatedAt().atOffset(ZoneOffset.UTC),
                confirmedProofAnchor.id()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean markReconcilePending(ProofAnchor proofAnchor) {
        int updatedRowCount = jdbcTemplate.update(
                MARK_RECONCILE_PENDING,
                proofAnchor.state().name(),
                proofAnchor.externalReference(),
                proofAnchor.updatedAt().atOffset(ZoneOffset.UTC),
                proofAnchor.id()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean confirmReconciled(ProofAnchor proofAnchor) {
        return updateReconcilePending(proofAnchor);
    }

    @Override
    public boolean resumePrepared(ProofAnchor proofAnchor) {
        return updateReconcilePending(proofAnchor);
    }

    @Override
    public boolean markManualReview(ProofAnchor proofAnchor) {
        return updateReconcilePending(proofAnchor);
    }

    private boolean updateReconcilePending(ProofAnchor proofAnchor) {
        int updatedRowCount = jdbcTemplate.update(
                UPDATE_RECONCILE_PENDING,
                proofAnchor.state().name(),
                proofAnchor.externalReference(),
                chainId(proofAnchor),
                contractAddress(proofAnchor),
                transactionHash(proofAnchor),
                blockNumber(proofAnchor),
                blockHash(proofAnchor),
                confirmationCount(proofAnchor),
                readBackCommitment(proofAnchor),
                chainCheckedAt(proofAnchor),
                proofAnchor.updatedAt().atOffset(ZoneOffset.UTC),
                proofAnchor.id()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean resumeReconciliation(ProofAnchor proofAnchor) {
        int updatedRowCount = jdbcTemplate.update(
                RESUME_RECONCILIATION,
                proofAnchor.state().name(),
                proofAnchor.updatedAt().atOffset(ZoneOffset.UTC),
                proofAnchor.id()
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
                mapEvmEvidence(resultSet),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }

    private EvmAnchorEvidence mapEvmEvidence(ResultSet resultSet) throws SQLException {
        BigDecimal chainId = resultSet.getBigDecimal("chain_id");
        if (chainId == null) {
            return null;
        }
        return new EvmAnchorEvidence(
                chainId.toBigIntegerExact(),
                resultSet.getString("contract_address"),
                resultSet.getString("transaction_hash"),
                resultSet.getBigDecimal("block_number").toBigIntegerExact(),
                resultSet.getString("block_hash"),
                resultSet.getInt("confirmation_count"),
                resultSet.getBytes("read_back_commitment"),
                resultSet.getObject("chain_checked_at", OffsetDateTime.class).toInstant()
        );
    }

    private BigInteger chainId(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).chainId();
    }

    private String contractAddress(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).contractAddress();
    }

    private String transactionHash(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).transactionHash();
    }

    private BigInteger blockNumber(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).blockNumber();
    }

    private String blockHash(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).blockHash();
    }

    private Integer confirmationCount(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null ? null : evidence(proofAnchor).confirmations();
    }

    private byte[] readBackCommitment(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null
                ? null
                : evidence(proofAnchor).readBackCommitment();
    }

    private OffsetDateTime chainCheckedAt(ProofAnchor proofAnchor) {
        return evidence(proofAnchor) == null
                ? null
                : evidence(proofAnchor).checkedAt().atOffset(ZoneOffset.UTC);
    }

    private EvmAnchorEvidence evidence(ProofAnchor proofAnchor) {
        return proofAnchor.evmEvidence();
    }
}
