package com.naesan.share.application.port.out;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.naesan.share.domain.PublicShare;

public interface PublicShareRepository {

    void save(PublicShare publicShare);

    void update(PublicShare publicShare);

    int revokeAllByPassportId(UUID passportId, Instant revokedAt);

    Optional<PublicShare> findByIdAndPassportId(UUID shareId, UUID passportId);

    Optional<PublicShare> findUnrevokedByPassportId(UUID passportId);

    Optional<PublicShare> findByTokenHash(byte[] tokenHash);
}
