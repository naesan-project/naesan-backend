package com.naesan.operations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("production")
@Configuration(proxyBeanMethods = false)
public class ProductionProfileConfiguration {

    @Bean
    ProductionEnvironmentGuard productionEnvironmentGuard(
            @Value("${naesan.security.frontend-origin}") String frontendOrigin,
            @Value("${naesan.storage.provider}") String storageProvider,
            @Value("${naesan.security.token.refresh-cookie-secure:false}")
            boolean secureRefreshCookie,
            @Value("${naesan.proof.provider}") String proofProvider,
            @Value("${naesan.proof.worker.enabled}") boolean proofWorkerEnabled
    ) {
        return new ProductionEnvironmentGuard(
                frontendOrigin,
                storageProvider,
                secureRefreshCookie,
                proofProvider,
                proofWorkerEnabled
        );
    }
}
