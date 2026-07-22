package com.naesan.share.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.domain.Passport;
import com.naesan.share.application.port.out.GeneratedPublicShareToken;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.share.application.port.out.PublicShareTokenCodec;
import com.naesan.share.domain.PublicShare;
import com.naesan.share.domain.PublicShareCapability;

public class ManagePublicShareService {
    private final PassportRepository passportRepository;
    private final PublicShareRepository publicShareRepository;
    private final PublicShareTokenCodec tokenCodec;
    private final Duration timeToLive;
    private final Clock clock;

    public ManagePublicShareService(
            PassportRepository passportRepository,
            PublicShareRepository publicShareRepository,
            PublicShareTokenCodec tokenCodec,
            Duration timeToLive,
            Clock clock
    ) {
        this.passportRepository = Objects.requireNonNull(passportRepository);
        this.publicShareRepository = Objects.requireNonNull(publicShareRepository);
        this.tokenCodec = Objects.requireNonNull(tokenCodec);
        this.timeToLive = requirePositive(timeToLive);
        this.clock = Objects.requireNonNull(clock);
    }

    private Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Public share TTL은 0보다 커야 합니다.");
        }
        return duration;
    }

    @Transactional
    public IssuedPublicShare issue(
            UUID holderAccountId,
            UUID passportId,
            PublicShareCapability capability
    ) {
        requireOwnedActivePassport(holderAccountId, passportId);
        Instant issuedAt = currentTime();
        publicShareRepository.findUnrevokedByPassportId(passportId)
                .ifPresent(publicShare -> handleExistingShare(publicShare, issuedAt));
        return createShare(passportId, capability, issuedAt);
    }

    private Passport requireOwnedActivePassport(
            UUID holderAccountId,
            UUID passportId
    ) {
        return passportRepository.findByIdForUpdate(passportId)
                .filter(passport -> passport.isActiveHolder(holderAccountId))
                .orElseThrow(PublicShareException::notFound);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private void handleExistingShare(PublicShare publicShare, Instant issuedAt) {
        if (publicShare.isAvailableAt(issuedAt)) {
            throw PublicShareException.alreadyActive();
        }
        publicShareRepository.update(publicShare.revoke(issuedAt));
    }

    private IssuedPublicShare createShare(
            UUID passportId,
            PublicShareCapability capability,
            Instant issuedAt
    ) {
        GeneratedPublicShareToken generatedToken = tokenCodec.generate();
        PublicShare publicShare = PublicShare.issue(
                UUID.randomUUID(),
                passportId,
                generatedToken.tokenHash(),
                capability,
                issuedAt.plus(timeToLive),
                issuedAt
        );
        publicShareRepository.save(publicShare);
        return new IssuedPublicShare(publicShare, generatedToken.rawToken());
    }

    @Transactional
    public IssuedPublicShare rotate(
            UUID holderAccountId,
            UUID passportId,
            PublicShareCapability capability
    ) {
        requireOwnedActivePassport(holderAccountId, passportId);
        Instant rotatedAt = currentTime();
        PublicShare currentShare = publicShareRepository
                .findUnrevokedByPassportId(passportId)
                .orElseThrow(PublicShareException::notFound);
        publicShareRepository.update(currentShare.revoke(rotatedAt));
        return createShare(passportId, capability, rotatedAt);
    }

    @Transactional
    public void revoke(
            UUID holderAccountId,
            UUID passportId,
            UUID shareId
    ) {
        requireOwnedActivePassport(holderAccountId, passportId);
        PublicShare publicShare = publicShareRepository
                .findByIdAndPassportId(shareId, passportId)
                .orElseThrow(PublicShareException::notFound);
        publicShareRepository.update(publicShare.revoke(currentTime()));
    }
}
