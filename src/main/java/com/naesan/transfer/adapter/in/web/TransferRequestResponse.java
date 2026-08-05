package com.naesan.transfer.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.transfer.application.ListedTransferRequest;

public record TransferRequestResponse(
        UUID id,
        UUID passportId,
        String requesterEmail,
        String recipientEmail,
        ProductResponse product,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {

    public static TransferRequestResponse from(ListedTransferRequest request) {
        return new TransferRequestResponse(
                request.id(),
                request.passportId(),
                request.requesterEmail(),
                request.recipientEmail(),
                new ProductResponse(
                        request.productName(),
                        request.merchantName()
                ),
                request.status().name(),
                request.expiresAt(),
                request.createdAt(),
                request.updatedAt()
        );
    }

    public record ProductResponse(
            String name,
            String merchantName
    ) {
    }
}
