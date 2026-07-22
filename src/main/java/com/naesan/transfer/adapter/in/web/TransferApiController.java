package com.naesan.transfer.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.security.AuthenticatedAccount;
import com.naesan.transfer.application.CreateTransferRequestService;
import com.naesan.transfer.application.CreatedTransferRequest;

@RestController
@RequestMapping("/api/passports/{passportId}/transfers")
public class TransferApiController {
    private final CreateTransferRequestService createTransferRequestService;

    public TransferApiController(
            CreateTransferRequestService createTransferRequestService
    ) {
        this.createTransferRequestService = createTransferRequestService;
    }

    @PostMapping
    public ResponseEntity<CreatedTransferRequestResponse> create(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId,
            @Valid @RequestBody CreateTransferRequest request
    ) {
        CreatedTransferRequest createdRequest = createTransferRequestService.create(
                account.id(),
                passportId,
                request.recipientEmail()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreatedTransferRequestResponse.from(createdRequest));
    }
}
