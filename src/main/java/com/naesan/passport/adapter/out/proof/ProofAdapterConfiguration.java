package com.naesan.passport.adapter.out.proof;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import com.naesan.passport.application.port.out.ProofAnchorPort;

@Configuration(proxyBeanMethods = false)
public class ProofAdapterConfiguration {

    @Bean
    @ConditionalOnProperty(
            name = "naesan.proof.provider",
            havingValue = "fake",
            matchIfMissing = true
    )
    ProofAnchorPort fakeProofAnchorPort(Clock clock) {
        return new FakeProofAnchorAdapter(clock);
    }

    @Bean
    ProofProviderGuard proofProviderGuard(
            @Value("${naesan.proof.provider:fake}") String provider,
            Environment environment
    ) {
        return new ProofProviderGuard(provider, environment.getActiveProfiles());
    }
}
