package com.naesan.passport.application.port.out;

import java.util.Optional;
import java.util.UUID;

import com.naesan.passport.domain.ProofAnchor;

public interface ProofAnchorRepository {

    void save(ProofAnchor proofAnchor);

    Optional<ProofAnchor> findById(UUID proofAnchorId);

    Optional<ProofAnchor> findByPassportId(UUID passportId);

    boolean confirmPrepared(ProofAnchor confirmedProofAnchor);

    boolean markReconcilePending(ProofAnchor proofAnchor);
}
