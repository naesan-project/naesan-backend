package com.naesan.passport.adapter.out.observability;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.naesan.passport.application.port.out.ProofOutboxTelemetry;
import com.naesan.passport.domain.OutboxEventStatus;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class MicrometerProofOutboxTelemetry implements ProofOutboxTelemetry {
    private static final String STATUS_TAG = "status";

    private final Map<OutboxEventStatus, Counter> processedCounters =
            new EnumMap<>(OutboxEventStatus.class);
    private final Map<OutboxEventStatus, Timer> processingTimers =
            new EnumMap<>(OutboxEventStatus.class);
    private final Map<OutboxEventStatus, DistributionSummary> attemptSummaries =
            new EnumMap<>(OutboxEventStatus.class);
    private final Map<OutboxEventStatus, AtomicLong> statusCounts =
            new EnumMap<>(OutboxEventStatus.class);
    private final Counter finalizeRejectedCounter;

    public MicrometerProofOutboxTelemetry(MeterRegistry meterRegistry) {
        for (OutboxEventStatus status : OutboxEventStatus.values()) {
            String statusTag = status.name().toLowerCase(Locale.ROOT);
            processedCounters.put(
                    status,
                    Counter.builder("naesan.proof.outbox.processed")
                            .tag(STATUS_TAG, statusTag)
                            .register(meterRegistry)
            );
            processingTimers.put(
                    status,
                    Timer.builder("naesan.proof.outbox.processing")
                            .tag(STATUS_TAG, statusTag)
                            .register(meterRegistry)
            );
            attemptSummaries.put(
                    status,
                    DistributionSummary.builder("naesan.proof.outbox.attempts")
                            .tag(STATUS_TAG, statusTag)
                            .register(meterRegistry)
            );
            AtomicLong statusCount = new AtomicLong();
            statusCounts.put(status, statusCount);
            Gauge.builder(
                            "naesan.proof.outbox.events",
                            statusCount,
                            AtomicLong::get
                    )
                    .tag(STATUS_TAG, statusTag)
                    .register(meterRegistry);
        }
        finalizeRejectedCounter = Counter
                .builder("naesan.proof.outbox.finalize.rejected")
                .register(meterRegistry);
    }

    @Override
    public void recordProcessed(
            OutboxEventStatus status,
            int attemptCount,
            Duration duration
    ) {
        processedCounters.get(status).increment();
        processingTimers.get(status).record(duration);
        attemptSummaries.get(status).record(attemptCount);
    }

    @Override
    public void recordFinalizeRejected() {
        finalizeRejectedCounter.increment();
    }

    @Override
    public void updateStatusCount(OutboxEventStatus status, long count) {
        statusCounts.get(status).set(count);
    }
}
