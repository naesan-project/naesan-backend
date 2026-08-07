package com.naesan.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PortfolioCaseStudyDocumentationTest {
    private static final Path CASE_STUDY_DIRECTORY = Path.of(
            "docs/case-studies"
    );
    private static final List<String> CASE_STUDIES = List.of(
            "01-ambiguous-transaction-recovery.md",
            "02-rpc-resilience-and-readiness.md",
            "03-chain-reorganization.md"
    );

    @Test
    @DisplayName("공개 트러블슈팅 사례는 문제와 실험과 결과와 트레이드오프를 포함한다")
    void documentsEvidenceBackedTroubleshooting() throws IOException {
        for (String fileName : CASE_STUDIES) {
            String content = Files.readString(
                    CASE_STUDY_DIRECTORY.resolve(fileName)
            );

            assertThat(content)
                    .contains("## 문제")
                    .contains("## 실험")
                    .contains("## 결과")
                    .contains("## 트레이드오프")
                    .doesNotContain("NAESAN_EVM_PRIVATE_KEY")
                    .doesNotContain("NAESAN_EVM_RPC_URL=")
                    .doesNotContain("@gmail.com");
        }
    }

    @Test
    @DisplayName("README는 공개 사례와 Web3 한계 문서를 연결한다")
    void linksCaseStudiesFromReadmes() throws IOException {
        String repositoryReadme = Files.readString(Path.of("README.md"));
        String caseStudyReadme = Files.readString(
                CASE_STUDY_DIRECTORY.resolve("README.md")
        );

        assertThat(repositoryReadme)
                .contains("docs/case-studies/README.md")
                .contains("Spring test suite는 390개")
                .contains("EVM suite는 25개");
        assertThat(caseStudyReadme)
                .contains(CASE_STUDIES.toArray(String[]::new))
                .contains("WEB3-LIMITATIONS.md");
    }
}
