package com.naesan.passport.adapter.out.observability;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.naesan.passport.application.port.out.ProofProviderTelemetry;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
public class MicrometerProofProviderTelemetry implements ProofProviderTelemetry {
    private static final String RESULT_TAG = "result";

    private final Map<ProbeStatus, Counter> probeCounters =
            new EnumMap<>(ProbeStatus.class);
    private final Map<ProbeStatus, Timer> probeTimers =
            new EnumMap<>(ProbeStatus.class);
    private final AtomicLong available = new AtomicLong();
    private final Timer outageTimer;

    public MicrometerProofProviderTelemetry(MeterRegistry meterRegistry) {
        for (ProbeStatus status : ProbeStatus.values()) {
            String resultTag = status.name().toLowerCase(Locale.ROOT);
            probeCounters.put(
                    status,
                    Counter.builder("naesan.proof.provider.probes")
                            .tag(RESULT_TAG, resultTag)
                            .register(meterRegistry)
            );
            probeTimers.put(
                    status,
                    Timer.builder("naesan.proof.provider.probe")
                            .tag(RESULT_TAG, resultTag)
                            .register(meterRegistry)
            );
        }
        Gauge.builder(
                        "naesan.proof.provider.available",
                        available,
                        AtomicLong::get
                )
                .register(meterRegistry);
        outageTimer = Timer.builder("naesan.proof.provider.outage")
                .register(meterRegistry);
    }

    @Override
    public void recordProbe(ProbeStatus status, Duration duration) {
        probeCounters.get(status).increment();
        probeTimers.get(status).record(duration);
        available.set(status == ProbeStatus.AVAILABLE ? 1 : 0);
    }

    @Override
    public void recordRecovery(Duration outageDuration) {
        outageTimer.record(outageDuration);
    }
}
