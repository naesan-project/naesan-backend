package com.naesan.share.application.port.out;

import java.util.Optional;

import com.naesan.share.application.PublicShareVerificationSource;

public interface PublicShareVerificationRepository {

    Optional<PublicShareVerificationSource> findByTokenHash(byte[] tokenHash);
}
