package com.naesan.passport.adapter.out.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.OutboxEventRepository;
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
