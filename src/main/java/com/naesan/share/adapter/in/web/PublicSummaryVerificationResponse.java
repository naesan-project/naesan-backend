package com.naesan.share.adapter.in.web;

import java.time.LocalDate;

import com.naesan.share.application.PublicPassportVerification;
import com.naesan.share.application.PublicEvmAnchorVerification;

public record PublicSummaryVerificationResponse(
        String capability,
        String productName,
        LocalDate purchasedAt,
        String passportStatus,
        String trustStage,
        String commitment,
        PublicEvmAnchorVerification evm
) implements PublicVerificationResponse {

    public static PublicSummaryVerificationResponse from(
            PublicPassportVerification verification
    ) {
        return new PublicSummaryVerificationResponse(
                verification.capability().name(),
                verification.productName(),
                verification.purchasedAt(),
                verification.passportStatus(),
                verification.trustStage(),
                verification.commitment(),
                verification.evm()
        );
    }
}
