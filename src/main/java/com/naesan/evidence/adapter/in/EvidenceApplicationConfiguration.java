package com.naesan.evidence.adapter.in;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.application.StoreTemporaryEvidenceFileService;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.evidence.application.port.out.PurchaseEvidenceRepository;

@Configuration(proxyBeanMethods = false)
public class EvidenceApplicationConfiguration {

    @Bean
    CreateEvidenceDraftService createEvidenceDraftService(
            PurchaseEvidenceRepository evidenceRepository,
            Clock clock
    ) {
        return new CreateEvidenceDraftService(evidenceRepository, clock);
    }

    @Bean
    StoreTemporaryEvidenceFileService storeTemporaryEvidenceFileService(
            FileStorage fileStorage,
            @Value("${naesan.evidence.file.max-size}") DataSize maximumFileSize
    ) {
        return new StoreTemporaryEvidenceFileService(
                fileStorage,
                maximumFileSize.toBytes()
        );
    }
}
