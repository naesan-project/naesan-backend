package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.naesan.passport.application.port.out.ProofAnchorCommand;

class FakeProofAnchorAdapterTest {
    private static final Instant ANCHORED_AT = Instant.parse("2026-07-18T00:00:00Z");
    private static final String COMMITMENT = "a".repeat(64);

    private final FakeProofAnchorAdapter adapter = new FakeProofAnchorAdapter(
            Clock.fixed(ANCHORED_AT, ZoneOffset.UTC)
    );

    @Test
    @DisplayName("Fake provider는 lookup과 commitment dedupe capability를 명시한다")
    void exposesCapabilities() {
        assertThat(adapter.capabilities().lookupSupported()).isTrue();
        assertThat(adapter.capabilities().commitmentDeduplicationSupported()).isTrue();
    }

    @Test
    @DisplayName("같은 commitment 제출은 같은 외부 증명 결과를 반환한다")
    void deduplicatesByCommitment() {
        var first = adapter.submit(new ProofAnchorCommand(
                "proof-anchor:first",
                COMMITMENT
        ));
        var second = adapter.submit(new ProofAnchorCommand(
                "proof-anchor:second",
                COMMITMENT
        ));

        assertThat(second).isEqualTo(first);
        assertThat(adapter.lookup(COMMITMENT)).contains(first);
    }

    @Test
    @DisplayName("Production profile에서 fake provider를 거절한다")
    void rejectsFakeProviderInProduction() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProofAdapterConfiguration.class)
                .withBean(Clock.class, Clock::systemUTC)
                .withPropertyValues(
                        "spring.profiles.active=production",
                        "naesan.proof.provider=fake"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessage(
                                    "Production profile에서는 fake proof provider를 사용할 수 없습니다."
                            );
                });
    }
}
