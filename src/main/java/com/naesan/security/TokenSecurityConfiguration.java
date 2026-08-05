package com.naesan.security;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.account.application.port.out.AccountRepository;

@Configuration(proxyBeanMethods = false)
public class TokenSecurityConfiguration {

    @Bean
    SecretKey jwtSecretKey(
            @Value("${naesan.security.token.jwt-secret}") String encodedSecret
    ) {
        byte[] secret = Base64.getDecoder().decode(encodedSecret);
        if (secret.length < 32) {
            throw new IllegalArgumentException("JWT secret은 256 bit 이상이어야 합니다.");
        }
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey jwtSecretKey,
            @Value("${naesan.security.token.issuer}") String issuer,
            @Value("${naesan.security.token.audience}") String audience
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtSecretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        OAuth2TokenValidator<Jwt> issuerValidator =
                JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = jwt ->
                jwt.getAudience().contains(audience)
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                                "invalid_token",
                                "JWT audience가 올바르지 않습니다.",
                                null
                        ));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                issuerValidator,
                audienceValidator
        ));
        return decoder;
    }

    @Bean
    JwtAccessTokenIssuer jwtAccessTokenIssuer(
            JwtEncoder jwtEncoder,
            Clock clock,
            @Value("${naesan.security.token.issuer}") String issuer,
            @Value("${naesan.security.token.audience}") String audience,
            @Value("${naesan.security.token.access-time-to-live}") Duration timeToLive
    ) {
        return new JwtAccessTokenIssuer(
                jwtEncoder,
                clock,
                issuer,
                audience,
                timeToLive
        );
    }

    @Bean
    RefreshTokenCodec refreshTokenCodec() {
        return new SecureRefreshTokenCodec(new SecureRandom());
    }

    @Bean
    TokenSessionManager tokenSessionManager(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec refreshTokenCodec,
            AccountRepository accountRepository,
            JwtAccessTokenIssuer accessTokenIssuer,
            PlatformTransactionManager transactionManager,
            Clock clock,
            @Value("${naesan.security.token.refresh-time-to-live}")
            Duration refreshTokenTimeToLive
    ) {
        return new TokenSessionManager(
                refreshTokenRepository,
                refreshTokenCodec,
                accountRepository,
                accessTokenIssuer,
                new TransactionTemplate(transactionManager),
                clock,
                refreshTokenTimeToLive
        );
    }
}
