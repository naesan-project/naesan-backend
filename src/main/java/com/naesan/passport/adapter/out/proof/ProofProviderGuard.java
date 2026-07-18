package com.naesan.passport.adapter.out.proof;

import java.util.Arrays;

public final class ProofProviderGuard {
    private static final String FAKE_PROVIDER = "fake";
    private static final String PRODUCTION_PROFILE = "production";

    public ProofProviderGuard(String provider, String[] activeProfiles) {
        boolean production = Arrays.asList(activeProfiles)
                .contains(PRODUCTION_PROFILE);
        if (production && FAKE_PROVIDER.equals(provider)) {
            throw new IllegalStateException(
                    "Production profile에서는 fake proof provider를 사용할 수 없습니다."
            );
        }
    }
}
