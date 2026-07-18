package com.naesan.passport.adapter.in;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.evidence.application.port.out.EvidenceSnapshotRepository;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.port.out.AnchorSaltGenerator;
import com.naesan.passport.application.port.out.OutboxEventRepository;
import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.application.port.out.ProofAnchorRepository;
import com.naesan.passport.domain.AnchorCommitmentCalculator;

@Configuration(proxyBeanMethods = false)
public class PassportApplicationConfiguration {

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
