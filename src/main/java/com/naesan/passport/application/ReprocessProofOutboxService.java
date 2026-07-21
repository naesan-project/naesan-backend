package com.naesan.passport.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OutboxReprocessAuditRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.OutboxEvent;
import com.naesan.passport.domain.OutboxEventStatus;
import com.naesan.passport.domain.OutboxReprocessAudit;
import com.naesan.passport.domain.ProofAnchor;

public class ReprocessProofOutboxService {
    private final OutboxEventRepository outboxEventRepository;
    private final ProofAnchorRepository proofAnchorRepository;
    private final OutboxReprocessAuditRepository auditRepository;
    private final Clock clock;

    public ReprocessProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            OutboxReprocessAuditRepository auditRepository,
            Clock clock
    ) {
        this.outboxEventRepository = Objects.requireNonNull(outboxEventRepository);
        this.proofAnchorRepository = Objects.requireNonNull(proofAnchorRepository);
        this.auditRepository = Objects.requireNonNull(auditRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public OutboxReprocessAudit reprocess(ReprocessOutboxEventCommand command) {
        OutboxEvent previousEvent = outboxEventRepository.findById(command.eventId())
                .orElseThrow(OutboxOperationException::eventNotReprocessable);
        Instant requestedAt = clock.instant();
        OutboxEvent reprocessedEvent;
        try {
            reprocessedEvent = previousEvent.reprocess(requestedAt);
        } catch (IllegalStateException exception) {
            throw OutboxOperationException.eventNotReprocessable();
        }

        resumeProofReconciliation(previousEvent, requestedAt);
        int reprocessNumber = outboxEventRepository.reprocess(
                        previousEvent,
                        reprocessedEvent
                )
                .orElseThrow(OutboxOperationException::reprocessConflict);
        OutboxReprocessAudit audit = OutboxReprocessAudit.create(
                UUID.randomUUID(),
                previousEvent,
                reprocessedEvent,
                command.operatorId(),
                command.reason(),
                reprocessNumber,
                requestedAt
        );
        auditRepository.save(audit);
        return audit;
    }

    private void resumeProofReconciliation(
            OutboxEvent previousEvent,
            Instant requestedAt
    ) {
        if (previousEvent.status() != OutboxEventStatus.MANUAL_REVIEW) {
            return;
        }
        ProofAnchor proofAnchor = proofAnchorRepository.findById(
                        previousEvent.proofAnchorId()
                )
                .orElseThrow(OutboxOperationException::reprocessConflict);
        ProofAnchor resumedProof;
        try {
            resumedProof = proofAnchor.resumeReconciliation(requestedAt);
        } catch (IllegalStateException exception) {
            throw OutboxOperationException.reprocessConflict();
        }
        if (!proofAnchorRepository.resumeReconciliation(resumedProof)) {
            throw OutboxOperationException.reprocessConflict();
        }
    }
}
