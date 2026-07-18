package com.naesan.evidence.adapter.out.storage;

import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.naesan.evidence.application.port.out.FileStorage;

@Configuration(proxyBeanMethods = false)
public class EvidenceStorageConfiguration {

    @Bean
    FileStorage fileStorage(
            @Value("${naesan.storage.local.root}") String storageRoot
    ) {
        return new LocalFileStorage(Path.of(storageRoot));
    }
}
