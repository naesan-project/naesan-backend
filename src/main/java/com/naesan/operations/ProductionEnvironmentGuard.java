package com.naesan.operations;

import java.net.URI;

public final class ProductionEnvironmentGuard {
    private static final String S3_STORAGE_PROVIDER = "s3";
    private static final String UNCONFIGURED_PROOF_PROVIDER = "unconfigured";

    public ProductionEnvironmentGuard(
            String frontendOrigin,
            String storageProvider,
            boolean secureRefreshCookie,
            String proofProvider,
            boolean proofWorkerEnabled
    ) {
        requireHttpsFrontend(frontendOrigin);
        requireS3Storage(storageProvider);
        requireSecureRefreshCookie(secureRefreshCookie);
        requireConfiguredProofWorker(proofProvider, proofWorkerEnabled);
    }

    private void requireHttpsFrontend(String frontendOrigin) {
        URI origin = URI.create(frontendOrigin);
        if (!"https".equalsIgnoreCase(origin.getScheme())
                || origin.getHost() == null) {
            throw new IllegalStateException(
                    "Production frontend origin은 HTTPS여야 합니다."
            );
        }
    }

    private void requireS3Storage(String storageProvider) {
        if (!S3_STORAGE_PROVIDER.equals(storageProvider)) {
            throw new IllegalStateException(
                    "Production profile은 S3 storage를 사용해야 합니다."
            );
        }
    }

    private void requireSecureRefreshCookie(boolean secureRefreshCookie) {
        if (!secureRefreshCookie) {
            throw new IllegalStateException(
                    "Production refresh token cookie는 Secure여야 합니다."
            );
        }
    }

    private void requireConfiguredProofWorker(
            String proofProvider,
            boolean proofWorkerEnabled
    ) {
        if (UNCONFIGURED_PROOF_PROVIDER.equals(proofProvider)
                && proofWorkerEnabled) {
            throw new IllegalStateException(
                    "Proof provider가 미구성일 때 worker를 실행할 수 없습니다."
            );
        }
    }
}
