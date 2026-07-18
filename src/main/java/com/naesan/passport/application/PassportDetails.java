package com.naesan.passport.application;

import com.naesan.passport.domain.Passport;
import com.naesan.passport.domain.ProofAnchor;

public record PassportDetails(
        Passport passport,
        ProofAnchor proofAnchor
) {
}
