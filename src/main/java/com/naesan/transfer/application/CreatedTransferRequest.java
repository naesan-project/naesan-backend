package com.naesan.transfer.application;

import com.naesan.transfer.domain.TransferRequest;

public record CreatedTransferRequest(
        TransferRequest request,
        String recipientEmail
) {
}
