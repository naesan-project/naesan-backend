package com.naesan.evidence.adapter.in.web;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.naesan.evidence.application.AttachEvidenceFileService;
import com.naesan.evidence.application.CreateEvidenceDraftCommand;
import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceApiController {
    private final CreateEvidenceDraftService createEvidenceDraftService;
    private final AttachEvidenceFileService attachEvidenceFileService;

    public EvidenceApiController(
            CreateEvidenceDraftService createEvidenceDraftService,
            AttachEvidenceFileService attachEvidenceFileService
    ) {
        this.createEvidenceDraftService = createEvidenceDraftService;
        this.attachEvidenceFileService = attachEvidenceFileService;
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

    @PostMapping(
            path = "/{evidenceId}/file",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<EvidenceFileResponse> attachFile(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID evidenceId,
            @RequestPart("file") MultipartFile file
    ) throws IOException {
        EvidenceFile evidenceFile = attachEvidenceFileService.attach(
                account.id(),
                evidenceId,
                file.getInputStream(),
                file.getContentType()
        );
        URI location = URI.create("/api/evidence/" + evidenceId + "/file");
        return ResponseEntity.created(location)
                .body(EvidenceFileResponse.from(evidenceFile));
    }
}
