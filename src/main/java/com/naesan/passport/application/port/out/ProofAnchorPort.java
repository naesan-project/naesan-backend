package com.naesan.passport.application.port.out;

import java.util.Optional;

public interface ProofAnchorPort {

    ProofProviderCapabilities capabilities();

    ProofAnchorReceipt submit(ProofAnchorCommand command);

    Optional<ProofAnchorReceipt> lookup(String commitment);
}
