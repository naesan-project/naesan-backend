package com.naesan.passport.adapter.out.proof;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.scheduling.annotation.Scheduled;

import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;

public final class EvmProofHealthIndicator implements HealthIndicator {
    static final String SCHEDULER_BEAN_NAME = "evmProofHealthScheduler";
    private static final String PROVIDER = "evm";
    private static final String NOT_CHECKED = "NOT_CHECKED";
    private static final Logger LOGGER =
            LoggerFactory.getLogger(EvmProofHealthIndicator.class);

    private final Runnable verifyConfiguration;
    private final Clock clock;
    private final AtomicReference<ProbeResult> latestResult;

    public EvmProofHealthIndicator(
            EvmProofAnchorAdapter adapter,
            Clock clock
    ) {
        this(adapter::verifyConfiguration, clock);
    }

    EvmProofHealthIndicator(Runnable verifyConfiguration, Clock clock) {
        this.verifyConfiguration = Objects.requireNonNull(verifyConfiguration);
        this.clock = Objects.requireNonNull(clock);
        latestResult = new AtomicReference<>(ProbeResult.notChecked(clock.instant()));
    }

    @Scheduled(
            initialDelayString = "${naesan.proof.evm.health.initial-delay:0s}",
            fixedDelayString = "${naesan.proof.evm.health.interval:30s}",
            scheduler = SCHEDULER_BEAN_NAME
    )
    public void refresh() {
        ProbeResult nextResult = probe();
        ProbeResult previousResult = latestResult.getAndSet(nextResult);
        logTransition(previousResult, nextResult);
    }

    private ProbeResult probe() {
        Instant checkedAt = clock.instant();
        try {
            verifyConfiguration.run();
            return ProbeResult.available(checkedAt);
        } catch (ProofProviderException failure) {
            return ProbeResult.failed(failure, checkedAt);
        }
    }

    private void logTransition(ProbeResult previous, ProbeResult current) {
        if (previous.sameStateAs(current)) {
            return;
        }
        if (current.availability() == Availability.AVAILABLE) {
            LOGGER.atInfo()
                    .addKeyValue("event", "evm_proof_health_changed")
                    .addKeyValue("status", current.availability())
                    .log("EVM proof provider recovered");
            return;
        }
        LOGGER.atWarn()
                .addKeyValue("event", "evm_proof_health_changed")
                .addKeyValue("status", current.availability())
                .addKeyValue("failure_type", current.failureType())
                .addKeyValue("error_code", current.errorCode())
                .log("EVM proof provider is unavailable");
    }

    @Override
    public Health health() {
        ProbeResult result = latestResult.get();
        Health.Builder builder = switch (result.availability()) {
            case AVAILABLE -> Health.up();
            case TEMPORARILY_UNAVAILABLE -> Health.down();
            case NOT_CHECKED, MISCONFIGURED -> Health.outOfService();
        };
        builder.withDetail("provider", PROVIDER)
                .withDetail("checkedAt", result.checkedAt());
        if (result.errorCode() != null) {
            builder.withDetail("errorCode", result.errorCode());
        }
        if (result.failureType() != null) {
            builder.withDetail("failureType", result.failureType().name());
        }
        return builder.build();
    }

    private enum Availability {
        NOT_CHECKED,
        AVAILABLE,
        TEMPORARILY_UNAVAILABLE,
        MISCONFIGURED
    }

    private record ProbeResult(
            Availability availability,
            ProofFailureType failureType,
            String errorCode,
            Instant checkedAt
    ) {

        private static ProbeResult notChecked(Instant checkedAt) {
            return new ProbeResult(
                    Availability.NOT_CHECKED,
                    null,
                    NOT_CHECKED,
                    checkedAt
            );
        }

        private static ProbeResult available(Instant checkedAt) {
            return new ProbeResult(
                    Availability.AVAILABLE,
                    null,
                    null,
                    checkedAt
            );
        }

        private static ProbeResult failed(
                ProofProviderException failure,
                Instant checkedAt
        ) {
            Availability availability = failure.failureType() == ProofFailureType.PERMANENT
                    ? Availability.MISCONFIGURED
                    : Availability.TEMPORARILY_UNAVAILABLE;
            return new ProbeResult(
                    availability,
                    failure.failureType(),
                    failure.errorCode(),
                    checkedAt
            );
        }

        private boolean sameStateAs(ProbeResult other) {
            return availability == other.availability
                    && failureType == other.failureType
                    && Objects.equals(errorCode, other.errorCode);
        }
    }
}
