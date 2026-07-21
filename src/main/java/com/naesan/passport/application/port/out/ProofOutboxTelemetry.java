package com.naesan.passport.application.port.out;

import java.time.Duration;

import com.naesan.passport.domain.OutboxEventStatus;

public interface ProofOutboxTelemetry {

    void recordProcessed(
            OutboxEventStatus status,
            int attemptCount,
            Duration duration
    );

    void recordFinalizeRejected();

    void updateStatusCount(OutboxEventStatus status, long count);
}
