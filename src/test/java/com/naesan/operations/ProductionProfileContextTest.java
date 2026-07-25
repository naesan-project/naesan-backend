package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.naesan.TestcontainersConfiguration;
import com.naesan.evidence.adapter.out.storage.S3FileStorage;
import com.naesan.evidence.application.port.out.FileStorage;
import com.naesan.passport.application.ProcessProofOutboxService;
import com.naesan.passport.application.port.out.ProofAnchorPort;

@ActiveProfiles("production")
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "naesan.security.frontend-origin=https://naesan.example.com",
        "naesan.storage.s3.bucket=naesan-private",
        "naesan.storage.s3.region=ap-northeast-2"
})
class ProductionProfileContextTest {
    private final ApplicationContext applicationContext;
    private final FileStorage fileStorage;

    @Autowired
    ProductionProfileContextTest(
            ApplicationContext applicationContext,
            FileStorage fileStorage
    ) {
        this.applicationContext = applicationContext;
        this.fileStorage = fileStorage;
    }

    @Test
    @DisplayName("Production profile은 S3 storage로 시작하고 미구성 proof worker를 만들지 않는다")
    void startsWithProductionBoundaries() {
        assertThat(fileStorage).isInstanceOf(S3FileStorage.class);
        assertThat(applicationContext.getBeansOfType(ProofAnchorPort.class))
                .isEmpty();
        assertThat(applicationContext.getBeansOfType(
                ProcessProofOutboxService.class
        )).isEmpty();
    }
}
