package com.naesan.transfer.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.passport.application.port.out.OwnershipHistoryRepository;
import com.naesan.passport.application.port.out.PassportRepository;
import com.naesan.passport.domain.OwnershipHistory;
import com.naesan.passport.domain.Passport;
import com.naesan.share.application.port.out.PublicShareRepository;
import com.naesan.transfer.application.port.out.TransferRequestRepository;
import com.naesan.transfer.domain.TransferRequest;

public class AcceptTransferRequestService {
    private final TransferRequestRepository transferRequestRepository;
    private final PassportRepository passportRepository;
    private final OwnershipHistoryRepository ownershipHistoryRepository;
    private final PublicShareRepository publicShareRepository;
    private final Clock clock;

    public AcceptTransferRequestService(
            TransferRequestRepository transferRequestRepository,
            PassportRepository passportRepository,
            OwnershipHistoryRepository ownershipHistoryRepository,
            PublicShareRepository publicShareRepository,
            Clock clock
    ) {
        this.transferRequestRepository = Objects.requireNonNull(transferRequestRepository);
        this.passportRepository = Objects.requireNonNull(passportRepository);
        this.ownershipHistoryRepository = Objects.requireNonNull(ownershipHistoryRepository);
        this.publicShareRepository = Objects.requireNonNull(publicShareRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void accept(UUID recipientAccountId, UUID requestId) {
        TransferRequest requestReference = findRecipientRequest(
                recipientAccountId,
                requestId
        );
        Passport passport = findPassport(requestReference.passportId());
        TransferRequest request = findRecipientRequestForUpdate(
                recipientAccountId,
                requestId
        );
        Instant acceptedAt = currentTime();
        TransferRequest acceptedRequest = acceptRequest(
                request,
                recipientAccountId,
                acceptedAt
        );
        Passport transferredPassport = transferPassport(passport, request);
        persistAcceptance(
                passport,
                transferredPassport,
                acceptedRequest,
                acceptedAt
        );
    }

    private TransferRequest findRecipientRequest(
            UUID recipientAccountId,
            UUID requestId
    ) {
        return transferRequestRepository.findById(requestId)
                .filter(request -> request.recipientAccountId()
                        .equals(recipientAccountId))
                .orElseThrow(TransferException::notFound);
    }

    private Passport findPassport(UUID passportId) {
        return passportRepository.findByIdForUpdate(passportId)
                .orElseThrow(TransferException::notFound);
    }

    private TransferRequest findRecipientRequestForUpdate(
            UUID recipientAccountId,
            UUID requestId
    ) {
        return transferRequestRepository.findByIdForUpdate(requestId)
                .filter(request -> request.recipientAccountId()
                        .equals(recipientAccountId))
                .orElseThrow(TransferException::notFound);
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    private TransferRequest acceptRequest(
            TransferRequest request,
            UUID recipientAccountId,
            Instant acceptedAt
    ) {
        try {
            return request.acceptBy(recipientAccountId, acceptedAt);
        } catch (IllegalStateException exception) {
            throw TransferException.notPending();
        }
    }

    private Passport transferPassport(
            Passport passport,
            TransferRequest request
    ) {
        try {
            return passport.transferTo(
                    request.requesterAccountId(),
                    request.recipientAccountId()
            );
        } catch (IllegalStateException exception) {
            throw TransferException.holderChanged();
        }
    }

    private void persistAcceptance(
            Passport previousPassport,
            Passport transferredPassport,
            TransferRequest acceptedRequest,
            Instant acceptedAt
    ) {
        boolean passportUpdated = passportRepository.update(
                transferredPassport,
                previousPassport.version()
        );
        if (!passportUpdated) {
            throw TransferException.holderChanged();
        }
        transferRequestRepository.update(acceptedRequest);
        ownershipHistoryRepository.append(OwnershipHistory.recordTransfer(
                UUID.randomUUID(),
                previousPassport.id(),
                previousPassport.currentHolderAccountId(),
                transferredPassport.currentHolderAccountId(),
                acceptedAt
        ));
        publicShareRepository.revokeAllByPassportId(
                previousPassport.id(),
                acceptedAt
        );
    }
}
