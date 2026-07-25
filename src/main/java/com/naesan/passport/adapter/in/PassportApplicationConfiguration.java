package com.naesan.passport.adapter.in;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.GetPassportDetailsService;
import com.naesan.passport.application.ListPassportsService;
import com.naesan.passport.application.ListOwnershipHistoryService;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.ReprocessProofOutboxService;
import com.naesan.passport.application.OutboxRetryPolicy;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OutboxReprocessAuditRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.application.port.out.PassportQueryRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.application.port.out.ProofOutboxTelemetry;
import com.naesan.passport.domain.AnchorCommitmentCalculator;

@Configuration(proxyBeanMethods = false)
public class PassportApplicationConfiguration {

    @Bean
    ReprocessProofOutboxService reprocessProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            OutboxReprocessAuditRepository auditRepository,
            Clock clock
    ) {
        return new ReprocessProofOutboxService(
                outboxEventRepository,
                proofAnchorRepository,
                auditRepository,
                clock
        );
    }

    @Bean
    OutboxRetryPolicy outboxRetryPolicy(
            @Value("${naesan.proof.worker.maximum-attempts}")
            int maximumAttempts,
            @Value("${naesan.proof.worker.retry-base-delay}")
            Duration baseDelay,
            @Value("${naesan.proof.worker.retry-maximum-delay}")
            Duration maximumDelay
    ) {
        return new OutboxRetryPolicy(
                maximumAttempts,
                baseDelay,
                maximumDelay,
                ThreadLocalRandom.current()
        );
    }

    @Bean
    @ConditionalOnProperty(
            name = "naesan.proof.worker.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    ProcessProofOutboxService processProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            ProofAnchorPort proofAnchorPort,
            PlatformTransactionManager transactionManager,
            @Value("${naesan.proof.worker.lease-duration}")
            Duration leaseDuration,
            OutboxRetryPolicy retryPolicy,
            Clock clock,
            ProofOutboxTelemetry telemetry
    ) {
        return new ProcessProofOutboxService(
                outboxEventRepository,
                proofAnchorRepository,
                proofAnchorPort,
                new TransactionTemplate(transactionManager),
                leaseDuration,
                retryPolicy,
                clock,
                telemetry
        );
    }

    @Bean
    ListPassportsService listPassportsService(
            PassportQueryRepository passportQueryRepository
    ) {
        return new ListPassportsService(passportQueryRepository);
    }

    @Bean
    GetPassportDetailsService getPassportDetailsService(
            PassportQueryRepository passportQueryRepository
    ) {
        return new GetPassportDetailsService(passportQueryRepository);
    }

    @Bean
    ListOwnershipHistoryService listOwnershipHistoryService(
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository
    ) {
        return new ListOwnershipHistoryService(
                passportRepository,
                ownershipHistoryRepository
        );
    }

    @Bean
    AnchorCommitmentCalculator anchorCommitmentCalculator() {
        return new AnchorCommitmentCalculator();
    }

    @Bean
    IssuePassportService issuePassportService(
            EvidenceSnapshotRepository snapshotRepository,
            PurchaseEvidenceRepository evidenceRepository,
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository,
            ProofAnchorRepository proofAnchorRepository,
            OutboxEventRepository outboxEventRepository,
            AnchorSaltGenerator anchorSaltGenerator,
            AnchorCommitmentCalculator commitmentCalculator,
            Clock clock
    ) {
        return new IssuePassportService(
                snapshotRepository,
                evidenceRepository,
                passportRepository,
                ownershipHistoryRepository,
                proofAnchorRepository,
                outboxEventRepository,
                anchorSaltGenerator,
                commitmentCalculator,
                clock
        );
    }
}
