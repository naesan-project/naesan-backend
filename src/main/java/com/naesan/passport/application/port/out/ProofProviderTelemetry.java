package com.naesan.passport.application.port.out;

import java.time.Duration;

public interface ProofProviderTelemetry {

    void recordProbe(ProbeStatus status, Duration duration);

    void recordRecovery(Duration outageDuration);

    enum ProbeStatus {
        AVAILABLE,
        TEMPORARILY_UNAVAILABLE,
        MISCONFIGURED
    }
}
