package com.naesan.evidence.adapter.in.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.evidence.application.CreateEvidenceDraftCommand;
import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceApiController {
    private final CreateEvidenceDraftService createEvidenceDraftService;

    public EvidenceApiController(CreateEvidenceDraftService createEvidenceDraftService) {
        this.createEvidenceDraftService = createEvidenceDraftService;
    }

    @PostMapping
    public ResponseEntity<EvidenceDraftResponse> createDraft(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody CreateEvidenceDraftRequest request
    ) {
        PurchaseEvidence evidence = createEvidenceDraftService.create(
                new CreateEvidenceDraftCommand(
                        account.id(),
                        request.merchantName(),
                        request.productName(),
                        request.serialNumber(),
                        request.purchasedAt(),
                        request.amount(),
                        request.currency()
                )
        );
        URI location = URI.create("/api/evidence/" + evidence.id());
        return ResponseEntity.created(location)
                .body(EvidenceDraftResponse.from(evidence));
    }
}
