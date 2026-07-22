package com.naesan.transfer.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import com.naesan.account.application.port.out.AccountRepository;
import com.naesan.account.domain.Account;
import com.naesan.account.domain.Email;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.transfer.application.port.out.TransferRequestRepository;
import com.naesan.transfer.domain.TransferRequest;

public class CreateTransferRequestService {
    private final AccountRepository accountRepository;
    private final PassportRepository passportRepository;
    private final TransferRequestRepository transferRequestRepository;
    private final Duration timeToLive;
    private final Clock clock;

    public CreateTransferRequestService(
            AccountRepository accountRepository,
            PassportRepository passportRepository,
            TransferRequestRepository transferRequestRepository,
            Duration timeToLive,
            Clock clock
    ) {
        this.accountRepository = Objects.requireNonNull(accountRepository);
        this.passportRepository = Objects.requireNonNull(passportRepository);
        this.transferRequestRepository = Objects.requireNonNull(transferRequestRepository);
        this.timeToLive = requirePositive(timeToLive);
        this.clock = Objects.requireNonNull(clock);
    }

    private Duration requirePositive(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("Transfer request TTL은 0보다 커야 합니다.");
        }
        return duration;
    }

    @Transactional
    public CreatedTransferRequest create(
            UUID requesterAccountId,
            UUID passportId,
            String recipientEmail
    ) {
        requireOwnedActivePassport(requesterAccountId, passportId);
        Instant createdAt = currentTime();
        releaseExpiredRequest(passportId, createdAt);
        Account recipient = findActiveRecipient(recipientEmail);
        requireDifferentAccount(requesterAccountId, recipient);
        TransferRequest request = createRequest(
                requesterAccountId,
                passportId,
                recipient,
                createdAt
        );
        save(request);
        return new CreatedTransferRequest(request, recipient.email().value());
    }

    private void requireOwnedActivePassport(
            UUID requesterAccountId,
            UUID passportId
    ) {
        passportRepository.findByIdForUpdate(passportId)
                .filter(passport -> passport.isActiveHolder(requesterAccountId))
                .orElseThrow(TransferException::notFound);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private void releaseExpiredRequest(UUID passportId, Instant createdAt) {
        transferRequestRepository.findPendingByPassportId(passportId)
                .ifPresent(request -> releaseIfExpired(request, createdAt));
    }

    private void releaseIfExpired(TransferRequest request, Instant createdAt) {
        if (request.isPendingAt(createdAt)) {
            throw TransferException.alreadyPending();
        }
        transferRequestRepository.update(request.expireIfDue(createdAt));
    }

    private Account findActiveRecipient(String recipientEmail) {
        Email email;
        try {
            email = new Email(recipientEmail);
        } catch (IllegalArgumentException exception) {
            throw TransferException.invalidRecipient();
        }
        return accountRepository.findByEmail(email)
                .filter(Account::canAuthenticate)
                .orElseThrow(TransferException::recipientNotFound);
    }

    private void requireDifferentAccount(
            UUID requesterAccountId,
            Account recipient
    ) {
        if (requesterAccountId.equals(recipient.id())) {
            throw TransferException.selfRequest();
        }
    }

    private TransferRequest createRequest(
            UUID requesterAccountId,
            UUID passportId,
            Account recipient,
            Instant createdAt
    ) {
        return TransferRequest.create(
                UUID.randomUUID(),
                passportId,
                requesterAccountId,
                recipient.id(),
                createdAt.plus(timeToLive),
                createdAt
        );
    }

    private void save(TransferRequest request) {
        try {
            transferRequestRepository.save(request);
        } catch (DuplicateKeyException exception) {
            throw TransferException.alreadyPending();
        }
    }
}
