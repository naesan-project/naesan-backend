package com.naesan.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.support.TransactionTemplate;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;

public final class TokenSessionManager {
    private final RefreshTokenRepository refreshTokenRepository;
    private final RefreshTokenCodec refreshTokenCodec;
    private final AccountRepository accountRepository;
    private final JwtAccessTokenIssuer accessTokenIssuer;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final Duration refreshTokenTimeToLive;

    public TokenSessionManager(
            RefreshTokenRepository refreshTokenRepository,
            RefreshTokenCodec refreshTokenCodec,
            AccountRepository accountRepository,
            JwtAccessTokenIssuer accessTokenIssuer,
            TransactionTemplate transactionTemplate,
            Clock clock,
            Duration refreshTokenTimeToLive
    ) {
        this.refreshTokenRepository = Objects.requireNonNull(refreshTokenRepository);
        this.refreshTokenCodec = Objects.requireNonNull(refreshTokenCodec);
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.accessTokenIssuer = Objects.requireNonNull(accessTokenIssuer);
        this.transactionTemplate = Objects.requireNonNull(transactionTemplate);
        this.clock = Objects.requireNonNull(clock);
        this.refreshTokenTimeToLive = Objects.requireNonNull(refreshTokenTimeToLive);
        if (refreshTokenTimeToLive.isNegative() || refreshTokenTimeToLive.isZero()) {
            throw new IllegalArgumentException("Refresh token 유효 기간은 0보다 커야 합니다.");
        }
    }

    public TokenSession start(Account account) {
        Instant now = Instant.now(clock);
        AuthenticatedAccount authenticatedAccount = AuthenticatedAccount.from(account, now);
        IssuedRefreshToken issuedRefreshToken = issueRefreshToken(account.id(), now);
        refreshTokenRepository.save(issuedRefreshToken.refreshToken());
        return tokenSession(authenticatedAccount, issuedRefreshToken);
    }

    private IssuedRefreshToken issueRefreshToken(UUID accountId, Instant issuedAt) {
        GeneratedRefreshToken generated = refreshTokenCodec.generate();
        Instant expiresAt = issuedAt.plus(refreshTokenTimeToLive);
        RefreshToken refreshToken = RefreshToken.issue(
                UUID.randomUUID(),
                accountId,
                generated.tokenHash(),
                issuedAt,
                expiresAt
        );
        return new IssuedRefreshToken(
                generated.rawToken(),
                refreshToken
        );
    }

    private TokenSession tokenSession(
            AuthenticatedAccount account,
            IssuedRefreshToken issuedRefreshToken
    ) {
        return new TokenSession(
                account,
                accessTokenIssuer.issue(account),
                issuedRefreshToken.rawToken(),
                issuedRefreshToken.refreshToken().expiresAt()
        );
    }

    public TokenSession refresh(String rawRefreshToken) {
        byte[] tokenHash = refreshTokenCodec.hash(rawRefreshToken)
                .orElseThrow(TokenSessionException::invalidRefreshToken);
        RotatedRefreshToken rotated = transactionTemplate.execute(status ->
                rotateRefreshToken(tokenHash)
        );
        if (rotated == null) {
            throw TokenSessionException.invalidRefreshToken();
        }
        AuthenticatedAccount account = AuthenticatedAccount.from(
                rotated.account(),
                Instant.now(clock)
        );
        return tokenSession(account, rotated.issuedRefreshToken());
    }

    private RotatedRefreshToken rotateRefreshToken(byte[] tokenHash) {
        Instant now = Instant.now(clock);
        RefreshToken current = refreshTokenRepository
                .findByTokenHashForUpdate(tokenHash)
                .orElseThrow(TokenSessionException::invalidRefreshToken);
        Account account = accountRepository.findById(current.accountId())
                .filter(Account::canAuthenticate)
                .orElseThrow(TokenSessionException::invalidRefreshToken);
        RefreshToken consumed = current.consume(now);
        IssuedRefreshToken replacement = issueRefreshToken(account.id(), now);

        refreshTokenRepository.update(consumed);
        refreshTokenRepository.save(replacement.refreshToken());
        return new RotatedRefreshToken(account, replacement);
    }

    public void revoke(String rawRefreshToken) {
        refreshTokenCodec.hash(rawRefreshToken).ifPresent(tokenHash ->
                transactionTemplate.executeWithoutResult(status ->
                        revokeRefreshToken(tokenHash)
                )
        );
    }

    private void revokeRefreshToken(byte[] tokenHash) {
        refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .map(token -> token.revoke(Instant.now(clock)))
                .ifPresent(refreshTokenRepository::update);
    }

    private record IssuedRefreshToken(
            String rawToken,
            RefreshToken refreshToken
    ) {
    }

    private record RotatedRefreshToken(
            Account account,
            IssuedRefreshToken issuedRefreshToken
    ) {
    }
}
