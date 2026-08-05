package com.naesan.security;

import java.util.Optional;

public interface RefreshTokenCodec {

    GeneratedRefreshToken generate();

    Optional<byte[]> hash(String rawToken);
}
