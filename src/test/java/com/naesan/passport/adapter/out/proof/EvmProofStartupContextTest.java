package com.naesan.passport.adapter.out.proof;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.naesan.passport.adapter.out.observability.MicrometerProofProviderTelemetry;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofProviderTelemetry;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class EvmProofStartupContextTest {
    private static final String CONTRACT_ADDRESS =
            "0x0000000000000000000000000000000000000001";
    private static final String PRIVATE_KEY = "1".repeat(64);

    @Test
    @DisplayName("EVM RPC가 연결되지 않아도 애플리케이션 context를 생성한다")
    void startsContextWhileRpcIsUnavailable() {
        new ApplicationContextRunner()
                .withUserConfiguration(ProofAdapterConfiguration.class)
                .withInitializer(context -> context.getBeanFactory()
                        .setConversionService(
                                ApplicationConversionService.getSharedInstance()
                        ))
                .withBean(Clock.class, Clock::systemUTC)
                .withBean(
                        ProofProviderTelemetry.class,
                        () -> new MicrometerProofProviderTelemetry(
                                new SimpleMeterRegistry()
                        )
                )
                .withPropertyValues(
                        "naesan.proof.provider=evm",
                        "naesan.proof.evm.rpc-url=http://127.0.0.1:1",
                        "naesan.proof.evm.chain-id=31337",
                        "naesan.proof.evm.contract-address=" + CONTRACT_ADDRESS,
                        "naesan.proof.evm.private-key=" + PRIVATE_KEY,
                        "naesan.proof.evm.deployment-block=0"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ProofAnchorPort.class);
                });
    }
}
