package com.naesan.transfer.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.transaction.annotation.Transactional;

import com.naesan.transfer.application.port.out.TransferRequestQueryRepository;
import com.naesan.transfer.domain.TransferRequest;

public class ListTransferRequestsService {
    private final TransferRequestQueryRepository queryRepository;
    private final Clock clock;

    public ListTransferRequestsService(
            TransferRequestQueryRepository queryRepository,
            Clock clock
    ) {
        this.queryRepository = Objects.requireNonNull(queryRepository);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public List<ListedTransferRequest> listOutgoing(UUID accountId) {
        return mapRequests(queryRepository.findAllOutgoing(accountId));
    }

    private List<ListedTransferRequest> mapRequests(
            List<TransferRequestDetails> details
    ) {
        Instant checkedAt = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        return details.stream()
                .map(detail -> mapRequest(detail, checkedAt))
                .toList();
    }

    private ListedTransferRequest mapRequest(
            TransferRequestDetails details,
            Instant checkedAt
    ) {
        TransferRequest request = details.request();
        return new ListedTransferRequest(
                request.id(),
                request.passportId(),
                details.requesterEmail(),
                details.recipientEmail(),
                details.productName(),
                details.merchantName(),
                request.statusAt(checkedAt),
                request.expiresAt(),
                request.createdAt(),
                request.updatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<ListedTransferRequest> listIncoming(UUID accountId) {
        return mapRequests(queryRepository.findAllIncoming(accountId));
    }
}
