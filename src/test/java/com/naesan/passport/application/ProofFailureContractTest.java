package com.naesan.passport.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.naesan.passport.application.port.out.ProofAnchorCommand;
import com.naesan.passport.application.port.out.ProofFailureType;
import com.naesan.passport.application.port.out.ProofProviderException;
import com.naesan.passport.support.ControllableProofAnchorAdapter;
import com.naesan.passport.support.ControllableProofAnchorAdapter.ProofOutcome;

class ProofFailureContractTest {
    private static final String COMMITMENT = "a".repeat(64);
    private static final ProofAnchorCommand COMMAND =
            new ProofAnchorCommand("proof-anchor:test", COMMITMENT);

    private final ControllableProofAnchorAdapter adapter =
            new ControllableProofAnchorAdapter(
                    Clock.fixed(
                            Instant.parse("2026-07-18T00:00:00Z"),
                            ZoneOffset.UTC
                    )
            );

    @Test
    @DisplayName("일시 오류는 retryable type과 제한된 code로 전달한다")
    void classifiesRetryableFailure() {
        adapter.setOutcome(ProofOutcome.RETRYABLE_FAILURE);

        assertThatThrownBy(() -> adapter.submit(COMMAND))
                .isInstanceOf(ProofProviderException.class)
                .satisfies(exception -> {
                    ProofProviderException providerException =
                            (ProofProviderException) exception;
                    assertThat(providerException.failureType())
                            .isEqualTo(ProofFailureType.RETRYABLE);
                    assertThat(providerException.errorCode())
                            .isEqualTo("PROVIDER_UNAVAILABLE");
                });
    }

    @Test
    @DisplayName("응답 유실은 외부 성공을 보존하고 ambiguous로 전달한다")
    void preservesSideEffectBeforeAmbiguousFailure() {
        adapter.setOutcome(ProofOutcome.SUCCESS_THEN_RESPONSE_LOSS);

        assertThatThrownBy(() -> adapter.submit(COMMAND))
                .isInstanceOf(ProofProviderException.class)
                .satisfies(exception -> assertThat(
                        ((ProofProviderException) exception).failureType()
                ).isEqualTo(ProofFailureType.AMBIGUOUS));
        assertThat(adapter.lookup(COMMITMENT)).isPresent();
        assertThat(adapter.submitCount()).isOne();
    }
}
