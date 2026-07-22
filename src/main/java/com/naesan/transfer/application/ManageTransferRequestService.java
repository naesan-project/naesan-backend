package com.naesan.transfer.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.transfer.application.port.out.TransferRequestRepository;
import com.naesan.transfer.domain.TransferRequest;

public class ManageTransferRequestService {
    private final TransferRequestRepository transferRequestRepository;
    private final Clock clock;

    public ManageTransferRequestService(
            TransferRequestRepository transferRequestRepository,
            Clock clock
    ) {
        this.transferRequestRepository = Objects.requireNonNull(transferRequestRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional
    public void cancel(UUID requesterAccountId, UUID requestId) {
        TransferRequest request = transferRequestRepository
                .findByIdForUpdate(requestId)
                .filter(foundRequest -> foundRequest.requesterAccountId()
                        .equals(requesterAccountId))
                .orElseThrow(TransferException::notFound);
        try {
            transferRequestRepository.update(request.cancelBy(
                    requesterAccountId,
                    currentTime()
            ));
        } catch (IllegalStateException exception) {
            throw TransferException.notPending();
        }
    }

    private Instant currentTime() {
        return clock.instant().truncatedTo(ChronoUnit.MILLIS);
    }

    @Transactional
    public void reject(UUID recipientAccountId, UUID requestId) {
        TransferRequest request = transferRequestRepository
                .findByIdForUpdate(requestId)
                .filter(foundRequest -> foundRequest.recipientAccountId()
                        .equals(recipientAccountId))
                .orElseThrow(TransferException::notFound);
        try {
            transferRequestRepository.update(request.rejectBy(
                    recipientAccountId,
                    currentTime()
            ));
        } catch (IllegalStateException exception) {
            throw TransferException.notPending();
        }
    }
}
