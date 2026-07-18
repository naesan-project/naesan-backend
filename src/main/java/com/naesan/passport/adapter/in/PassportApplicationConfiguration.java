package com.naesan.passport.adapter.in;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.GetPassportDetailsService;
import com.naesan.passport.application.ListPassportsService;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.application.port.out.PassportQueryRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.application.port.out.ProofAnchorPort;
import com.naesan.passport.domain.AnchorCommitmentCalculator;

@Configuration(proxyBeanMethods = false)
public class PassportApplicationConfiguration {

    @Bean
    ProcessProofOutboxService processProofOutboxService(
            OutboxEventRepository outboxEventRepository,
            ProofAnchorRepository proofAnchorRepository,
            ProofAnchorPort proofAnchorPort,
            PlatformTransactionManager transactionManager,
            @Value("${naesan.proof.worker.lease-duration}")
            Duration leaseDuration
    ) {
        return new ProcessProofOutboxService(
                outboxEventRepository,
                proofAnchorRepository,
                proofAnchorPort,
                new TransactionTemplate(transactionManager),
                leaseDuration
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
