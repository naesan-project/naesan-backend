package com.naesan.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

public final class JwtAccessTokenIssuer {
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final String issuer;
    private final String audience;
    private final Duration timeToLive;

    public JwtAccessTokenIssuer(
            JwtEncoder jwtEncoder,
            Clock clock,
            String issuer,
            String audience,
            Duration timeToLive
    ) {
        this.jwtEncoder = Objects.requireNonNull(jwtEncoder);
        this.clock = Objects.requireNonNull(clock);
        this.issuer = Objects.requireNonNull(issuer);
        this.audience = Objects.requireNonNull(audience);
        this.timeToLive = Objects.requireNonNull(timeToLive);
        if (timeToLive.isNegative() || timeToLive.isZero()) {
            throw new IllegalArgumentException("Access token 유효 기간은 0보다 커야 합니다.");
        }
    }

    public AccessToken issue(AuthenticatedAccount account) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(timeToLive);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(account.id().toString())
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", account.email())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();
        String token = jwtEncoder.encode(
                JwtEncoderParameters.from(header, claims)
        ).getTokenValue();
        return new AccessToken(token, expiresAt);
    }
}
