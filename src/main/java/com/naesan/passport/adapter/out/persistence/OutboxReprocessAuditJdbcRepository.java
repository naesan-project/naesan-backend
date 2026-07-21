package com.naesan.passport.adapter.out.persistence;

import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import com.naesan.passport.application.port.out.OutboxReprocessAuditRepository;
import com.naesan.passport.domain.OutboxReprocessAudit;

@Repository
public class OutboxReprocessAuditJdbcRepository
        implements OutboxReprocessAuditRepository {
    private static final String INSERT_AUDIT = """
            INSERT INTO outbox_reprocess_audit (
                id,
                outbox_event_id,
                proof_anchor_id,
                operator_id,
                reason,
                previous_status,
                new_status,
                previous_attempt_count,
                reprocess_number,
                requested_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public OutboxReprocessAuditJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void save(OutboxReprocessAudit audit) {
        jdbcTemplate.update(
                INSERT_AUDIT,
                audit.id(),
                audit.outboxEventId(),
                audit.proofAnchorId(),
                audit.operatorId(),
                audit.reason(),
                audit.previousStatus().name(),
                audit.newStatus().name(),
                audit.previousAttemptCount(),
                audit.reprocessNumber(),
                audit.requestedAt().atOffset(ZoneOffset.UTC)
        );
    }
}
