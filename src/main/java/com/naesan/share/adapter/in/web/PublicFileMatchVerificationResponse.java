package com.naesan.share.adapter.in.web;

import java.time.LocalDate;

import com.naesan.share.application.PublicPassportVerification;
import com.naesan.share.application.PublicVerificationMaterial;

public record PublicFileMatchVerificationResponse(
        String capability,
        String productName,
        LocalDate purchasedAt,
        String passportStatus,
        String trustStage,
        String commitment,
        VerificationMaterialResponse verificationMaterial
) implements PublicVerificationResponse {

    public static PublicFileMatchVerificationResponse from(
            PublicPassportVerification verification
    ) {
        return new PublicFileMatchVerificationResponse(
                verification.capability().name(),
                verification.productName(),
                verification.purchasedAt(),
                verification.passportStatus(),
                verification.trustStage(),
                verification.commitment(),
                VerificationMaterialResponse.from(verification.verificationMaterial())
        );
    }

    public record VerificationMaterialResponse(
            String snapshotDigest,
            String anchorSalt,
            String commitment,
            int snapshotSchemaVersion,
            int commitmentSchemaVersion,
            String domain,
            String snapshotHashAlgorithm,
            String commitmentHashAlgorithm,
            String commitmentEncoding
    ) {

        private static VerificationMaterialResponse from(
                PublicVerificationMaterial material
        ) {
            return new VerificationMaterialResponse(
                    material.snapshotDigest(),
                    material.anchorSalt(),
                    material.commitment(),
                    material.snapshotSchemaVersion(),
                    material.commitmentSchemaVersion(),
                    material.domain(),
                    material.snapshotHashAlgorithm(),
                    material.commitmentHashAlgorithm(),
                    material.commitmentEncoding()
            );
        }
    }
}
