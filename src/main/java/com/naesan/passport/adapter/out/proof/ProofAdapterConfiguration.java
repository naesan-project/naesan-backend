package com.naesan.passport.adapter.out.proof;

import java.math.BigInteger;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

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

    @Bean(name = "proofProvider")
    @ConditionalOnProperty(
            name = "naesan.proof.provider",
            havingValue = "fake",
            matchIfMissing = true
    )
    HealthIndicator fakeProofProviderHealthIndicator() {
        return () -> Health.up()
                .withDetail("provider", "fake")
                .build();
    }

    @Bean(name = "proofProvider")
    @ConditionalOnProperty(
            name = "naesan.proof.provider",
            havingValue = "unconfigured"
    )
    HealthIndicator unconfiguredProofProviderHealthIndicator() {
        return () -> Health.outOfService()
                .withDetail("provider", "unconfigured")
                .withDetail("errorCode", "PROVIDER_NOT_CONFIGURED")
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    Web3j evmWeb3j(@Value("${naesan.proof.evm.rpc-url}") URI rpcUrl) {
        return Web3j.build(new HttpService(rpcUrl.toString()));
    }

    @Bean
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    EvmProofProperties evmProofProperties(
            @Value("${naesan.proof.evm.rpc-url}") URI rpcUrl,
            @Value("${naesan.proof.evm.chain-id}") BigInteger chainId,
            @Value("${naesan.proof.evm.contract-address}") String contractAddress,
            @Value("${naesan.proof.evm.deployment-block}") BigInteger deploymentBlock,
            @Value("${naesan.proof.evm.required-confirmations:2}") int requiredConfirmations,
            @Value("${naesan.proof.evm.receipt-attempts:12}") int receiptAttempts,
            @Value("${naesan.proof.evm.receipt-poll-interval:1s}") Duration receiptPollInterval
    ) {
        return new EvmProofProperties(
                rpcUrl,
                chainId,
                contractAddress,
                deploymentBlock,
                requiredConfirmations,
                receiptAttempts,
                receiptPollInterval
        );
    }

    @Bean
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    Credentials evmWriterCredentials(
            @Value("${naesan.proof.evm.private-key}") String privateKey
    ) {
        if (privateKey == null
                || !privateKey.matches("(?:0x)?[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("EVM writer private key 형식이 유효하지 않습니다.");
        }
        return Credentials.create(privateKey);
    }

    @Bean
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    EvmProofAnchorAdapter evmProofAnchorPort(
            Web3j evmWeb3j,
            Credentials evmWriterCredentials,
            EvmProofProperties properties,
            Clock clock
    ) {
        return new EvmProofAnchorAdapter(
                evmWeb3j,
                evmWriterCredentials,
                properties,
                clock
        );
    }

    @Bean(name = "proofProvider")
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    EvmProofHealthIndicator evmProofProviderHealthIndicator(
            EvmProofAnchorAdapter evmProofAnchorAdapter,
            Clock clock
    ) {
        return new EvmProofHealthIndicator(evmProofAnchorAdapter, clock);
    }

    @Bean(
            name = EvmProofHealthIndicator.SCHEDULER_BEAN_NAME,
            destroyMethod = "shutdown"
    )
    @ConditionalOnProperty(name = "naesan.proof.provider", havingValue = "evm")
    TaskScheduler evmProofHealthScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("evm-proof-health-");
        return scheduler;
    }

    @Bean
    ProofProviderGuard proofProviderGuard(
            @Value("${naesan.proof.provider:fake}") String provider,
            Environment environment
    ) {
        return new ProofProviderGuard(provider, environment.getActiveProfiles());
    }
}
