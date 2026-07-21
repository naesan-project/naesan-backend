package com.naesan.share.application;

import com.naesan.passport.domain.ProofAnchorState;

public enum PublicTrustStage {
    INTERNALLY_SEALED,
    ANCHOR_SUBMITTED,
    ANCHOR_CONFIRMED;

    public static PublicTrustStage from(ProofAnchorState proofState) {
        return switch (proofState) {
            case SUBMITTED -> ANCHOR_SUBMITTED;
            case CONFIRMED -> ANCHOR_CONFIRMED;
            case PREPARED,
                    UNKNOWN,
                    RECONCILE_PENDING,
                    MANUAL_REVIEW -> INTERNALLY_SEALED;
        };
    }
}
