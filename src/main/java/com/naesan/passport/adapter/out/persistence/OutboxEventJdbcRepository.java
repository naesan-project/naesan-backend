package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.application.OutboxClaimRequest;
import com.naesan.passport.domain.OutboxClaim;
import com.naesan.passport.domain.OutboxClaimReason;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxEventStatus;

@Repository
public class OutboxEventJdbcRepository implements OutboxEventRepository {
    private static final String SELECT_COLUMNS = """
            SELECT
                id,
                event_type,
                aggregate_id,
                proof_anchor_id,
                schema_version,
                payload,
                dispatch_key,
                status,
                attempt_count,
                next_attempt_at,
                created_at,
                updated_at
            FROM outbox_events
            """;
    private static final String INSERT_OUTBOX_EVENT = """
            INSERT INTO outbox_events (
                id,
                event_type,
                aggregate_id,
                proof_anchor_id,
                schema_version,
                payload,
                dispatch_key,
                status,
                attempt_count,
                next_attempt_at,
                created_at,
                updated_at
            )
            VALUES (?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, ?, ?, ?, ?)
            """;
    private static final String FIND_BY_ID = SELECT_COLUMNS + " WHERE id = ?";
    private static final String FIND_BY_PROOF_ANCHOR_ID =
            SELECT_COLUMNS + " WHERE proof_anchor_id = ?";
    private static final String CLAIM_NEXT_DUE = """
            WITH next_event AS (
                SELECT id, status, claim_reason
                FROM outbox_events
                WHERE (
                    status IN ('PENDING', 'RETRY_WAIT', 'RECONCILE_PENDING')
                    AND next_attempt_at <= clock_timestamp()
                ) OR (
                    status = 'CLAIMED'
                    AND lease_until < clock_timestamp()
                )
                ORDER BY next_attempt_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE outbox_events event
            SET
                status = 'CLAIMED',
                attempt_count = event.attempt_count + 1,
                claimed_by = ?,
                claim_token = ?,
                fencing_version = event.fencing_version + 1,
                lease_until = clock_timestamp() + (? * INTERVAL '1 millisecond'),
                claim_reason = CASE
                    WHEN next_event.status = 'RECONCILE_PENDING'
                        THEN 'RECONCILIATION'
                    WHEN next_event.status = 'CLAIMED'
                        THEN next_event.claim_reason
                    ELSE 'SUBMISSION'
                END,
                updated_at = clock_timestamp()
            FROM next_event
            WHERE event.id = next_event.id
            RETURNING
                event.id,
                event.event_type,
                event.aggregate_id,
                event.proof_anchor_id,
                event.schema_version,
                event.payload,
                event.dispatch_key,
                event.status,
                event.attempt_count,
                event.next_attempt_at,
                event.created_at,
                event.updated_at,
                event.claim_token,
                event.fencing_version,
                event.lease_until,
                event.claimed_by,
                event.claim_reason
            """;
    private static final String COMPLETE_CLAIMED = """
            UPDATE outbox_events
            SET
                status = ?,
                updated_at = ?,
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND fencing_version = ?
            """;
    private static final String SCHEDULE_RETRY = """
            UPDATE outbox_events
            SET
                status = 'RETRY_WAIT',
                next_attempt_at =
                    clock_timestamp() + (? * INTERVAL '1 millisecond'),
                error_category = ?,
                error_code = ?,
                updated_at = clock_timestamp(),
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND fencing_version = ?
            """;
    private static final String MOVE_TO_DEAD_LETTER = """
            UPDATE outbox_events
            SET
                status = 'DEAD_LETTER',
                error_category = ?,
                error_code = ?,
                updated_at = clock_timestamp(),
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND fencing_version = ?
            """;
    private static final String SCHEDULE_RECONCILIATION = """
            UPDATE outbox_events
            SET
                status = 'RECONCILE_PENDING',
                next_attempt_at = clock_timestamp(),
                error_category = ?,
                error_code = ?,
                updated_at = clock_timestamp(),
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND fencing_version = ?
            """;
    private static final String MOVE_TO_MANUAL_REVIEW = """
            UPDATE outbox_events
            SET
                status = 'MANUAL_REVIEW',
                error_category = ?,
                error_code = ?,
                updated_at = clock_timestamp(),
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = 'CLAIMED'
              AND claim_token = ?
              AND fencing_version = ?
            """;
    private static final String REPROCESS = """
            UPDATE outbox_events
            SET
                status = ?,
                attempt_count = 0,
                next_attempt_at = ?,
                reprocess_count = reprocess_count + 1,
                error_category = NULL,
                error_code = NULL,
                updated_at = ?,
                claim_token = NULL,
                lease_until = NULL,
                claimed_by = NULL,
                claim_reason = NULL
            WHERE id = ?
              AND status = ?
            RETURNING reprocess_count
            """;
    private static final String COUNT_BY_STATUS = """
            SELECT status, COUNT(*) AS event_count
            FROM outbox_events
            GROUP BY status
            """;

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxEvent outboxEvent) {
        jdbcTemplate.update(
                INSERT_OUTBOX_EVENT,
                outboxEvent.id(),
                outboxEvent.eventType(),
                outboxEvent.aggregateId(),
                outboxEvent.proofAnchorId(),
                outboxEvent.schemaVersion(),
                outboxEvent.payload(),
                outboxEvent.dispatchKey(),
                outboxEvent.status().name(),
                outboxEvent.attemptCount(),
                outboxEvent.nextAttemptAt().atOffset(ZoneOffset.UTC),
                outboxEvent.createdAt().atOffset(ZoneOffset.UTC),
                outboxEvent.updatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public Optional<OutboxEvent> findById(UUID outboxEventId) {
        return findOne(FIND_BY_ID, outboxEventId);
    }

    private Optional<OutboxEvent> findOne(String sql, UUID id) {
        return jdbcTemplate.query(sql, this::mapOutboxEvent, id)
                .stream()
                .findFirst();
    }

    @Override
    public Optional<OutboxEvent> findByProofAnchorId(UUID proofAnchorId) {
        return findOne(FIND_BY_PROOF_ANCHOR_ID, proofAnchorId);
    }

    @Override
    public Optional<OutboxClaim> claimNextDue(OutboxClaimRequest request) {
        return jdbcTemplate.query(
                        CLAIM_NEXT_DUE,
                        this::mapClaim,
                        request.workerId(),
                        request.claimToken(),
                        request.leaseDuration().toMillis()
                )
                .stream()
                .findFirst();
    }

    private OutboxClaim mapClaim(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OutboxClaim(
                mapOutboxEvent(resultSet, rowNumber),
                resultSet.getObject("claim_token", UUID.class),
                resultSet.getLong("fencing_version"),
                resultSet.getObject("lease_until", OffsetDateTime.class).toInstant(),
                resultSet.getString("claimed_by"),
                OutboxClaimReason.valueOf(resultSet.getString("claim_reason"))
        );
    }

    @Override
    public boolean completeClaimed(
            OutboxClaim claim,
            OutboxEvent succeededEvent
    ) {
        int updatedRowCount = jdbcTemplate.update(
                COMPLETE_CLAIMED,
                succeededEvent.status().name(),
                succeededEvent.updatedAt().atOffset(ZoneOffset.UTC),
                succeededEvent.id(),
                claim.claimToken(),
                claim.fencingVersion()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean scheduleRetry(
            OutboxClaim claim,
            Duration delay,
            ProofProviderException failure
    ) {
        int updatedRowCount = jdbcTemplate.update(
                SCHEDULE_RETRY,
                delay.toMillis(),
                failure.failureType().name(),
                failure.errorCode(),
                claim.event().id(),
                claim.claimToken(),
                claim.fencingVersion()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean moveToDeadLetter(
            OutboxClaim claim,
            ProofProviderException failure
    ) {
        int updatedRowCount = jdbcTemplate.update(
                MOVE_TO_DEAD_LETTER,
                failure.failureType().name(),
                failure.errorCode(),
                claim.event().id(),
                claim.claimToken(),
                claim.fencingVersion()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean scheduleReconciliation(
            OutboxClaim claim,
            ProofProviderException failure
    ) {
        int updatedRowCount = jdbcTemplate.update(
                SCHEDULE_RECONCILIATION,
                failure.failureType().name(),
                failure.errorCode(),
                claim.event().id(),
                claim.claimToken(),
                claim.fencingVersion()
        );
        return updatedRowCount == 1;
    }

    @Override
    public boolean moveToManualReview(
            OutboxClaim claim,
            ProofProviderException failure
    ) {
        int updatedRowCount = jdbcTemplate.update(
                MOVE_TO_MANUAL_REVIEW,
                failure.failureType().name(),
                failure.errorCode(),
                claim.event().id(),
                claim.claimToken(),
                claim.fencingVersion()
        );
        return updatedRowCount == 1;
    }

    @Override
    public Optional<Integer> reprocess(
            OutboxEvent previousEvent,
            OutboxEvent reprocessedEvent
    ) {
        return jdbcTemplate.query(
                        REPROCESS,
                        (resultSet, rowNumber) -> resultSet.getInt("reprocess_count"),
                        reprocessedEvent.status().name(),
                        reprocessedEvent.nextAttemptAt().atOffset(ZoneOffset.UTC),
                        reprocessedEvent.updatedAt().atOffset(ZoneOffset.UTC),
                        previousEvent.id(),
                        previousEvent.status().name()
                )
                .stream()
                .findFirst();
    }

    @Override
    public Map<OutboxEventStatus, Long> countByStatus() {
        EnumMap<OutboxEventStatus, Long> counts = new EnumMap<>(
                OutboxEventStatus.class
        );
        jdbcTemplate.query(
                COUNT_BY_STATUS,
                (resultSet, rowNumber) -> Map.entry(
                        OutboxEventStatus.valueOf(resultSet.getString("status")),
                        resultSet.getLong("event_count")
                )
        ).forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    private OutboxEvent mapOutboxEvent(ResultSet resultSet, int rowNumber) throws SQLException {
        return OutboxEvent.restore(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("event_type"),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getObject("proof_anchor_id", UUID.class),
                resultSet.getInt("schema_version"),
                resultSet.getString("payload"),
                resultSet.getString("dispatch_key"),
                OutboxEventStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("attempt_count"),
                resultSet.getObject("next_attempt_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("created_at", OffsetDateTime.class).toInstant(),
                resultSet.getObject("updated_at", OffsetDateTime.class).toInstant()
        );
    }
}
