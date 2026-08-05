package com.naesan.share.application;

import java.time.LocalDate;

import com.naesan.share.domain.PublicShareCapability;

public record PublicPassportVerification(
        PublicShareCapability capability,
        String productName,
        LocalDate purchasedAt,
        String passportStatus,
        String trustStage,
        String commitment,
        PublicEvmAnchorVerification evm,
        PublicVerificationMaterial verificationMaterial
) {
}
