package com.naesan.transfer.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import com.naesan.transfer.application.CreatedTransferRequest;

public record CreatedTransferRequestResponse(
        UUID id,
        UUID passportId,
        String recipientEmail,
        String status,
        Instant expiresAt,
        Instant createdAt
) {

    public static CreatedTransferRequestResponse from(
            CreatedTransferRequest createdRequest
    ) {
        return new CreatedTransferRequestResponse(
                createdRequest.request().id(),
                createdRequest.request().passportId(),
                createdRequest.recipientEmail(),
                createdRequest.request().status().name(),
                createdRequest.request().expiresAt(),
                createdRequest.request().createdAt()
        );
    }
}
