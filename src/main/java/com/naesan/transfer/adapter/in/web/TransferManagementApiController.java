package com.naesan.transfer.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.security.AuthenticatedAccount;
import com.naesan.transfer.application.ListTransferRequestsService;
import com.naesan.transfer.application.ManageTransferRequestService;

@RestController
@RequestMapping("/api/transfers")
public class TransferManagementApiController {
    private final ManageTransferRequestService manageTransferRequestService;
    private final ListTransferRequestsService listTransferRequestsService;

    public TransferManagementApiController(
            ManageTransferRequestService manageTransferRequestService,
            ListTransferRequestsService listTransferRequestsService
    ) {
        this.manageTransferRequestService = manageTransferRequestService;
        this.listTransferRequestsService = listTransferRequestsService;
    }

    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID requestId
    ) {
        manageTransferRequestService.cancel(account.id(), requestId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{requestId}/rejection")
    public ResponseEntity<Void> reject(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID requestId
    ) {
        manageTransferRequestService.reject(account.id(), requestId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/outgoing")
    public List<TransferRequestResponse> listOutgoing(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return listTransferRequestsService.listOutgoing(account.id())
                .stream()
                .map(TransferRequestResponse::from)
                .toList();
    }

    @GetMapping("/incoming")
    public List<TransferRequestResponse> listIncoming(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return listTransferRequestsService.listIncoming(account.id())
                .stream()
                .map(TransferRequestResponse::from)
                .toList();
    }
}
