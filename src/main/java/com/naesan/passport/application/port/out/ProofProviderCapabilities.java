package com.naesan.passport.application.port.out;

public record ProofProviderCapabilities(
        boolean lookupSupported,
        boolean commitmentDeduplicationSupported
) {
}
