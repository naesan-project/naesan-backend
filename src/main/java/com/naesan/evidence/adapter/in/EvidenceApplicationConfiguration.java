package com.naesan.evidence.adapter.in;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

import com.naesan.evidence.application.StoreTemporaryEvidenceFileService;
import com.naesan.evidence.application.port.out.FileStorage;

@Configuration(proxyBeanMethods = false)
public class EvidenceApplicationConfiguration {

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
