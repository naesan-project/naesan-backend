package com.naesan.transfer.application;

import com.naesan.transfer.domain.TransferRequest;

public record TransferRequestDetails(
        TransferRequest request,
        String requesterEmail,
        String recipientEmail,
        String productName,
        String merchantName
) {
}
