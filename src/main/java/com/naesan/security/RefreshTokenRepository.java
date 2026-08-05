package com.naesan.security;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {

    void save(RefreshToken refreshToken);

    Optional<RefreshToken> findByTokenHashForUpdate(byte[] tokenHash);

    void update(RefreshToken refreshToken);

    void revokeAll(UUID accountId, Instant revokedAt);
}
