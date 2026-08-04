package com.naesan.passport.application;

import java.time.LocalDate;

import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.ProofAnchor;

public record PassportDetails(
        Passport passport,
        ProofAnchor proofAnchor,
        String productName,
        String merchantName,
        LocalDate purchasedAt
) {
}
