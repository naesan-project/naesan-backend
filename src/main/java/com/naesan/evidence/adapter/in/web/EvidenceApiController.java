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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.multipart.MultipartFile;

import com.naesan.evidence.application.AttachEvidenceFileService;
import com.naesan.evidence.application.CreateEvidenceDraftCommand;
import com.naesan.evidence.application.CreateEvidenceDraftService;
import com.naesan.evidence.application.ConfirmEvidenceService;
import com.naesan.evidence.application.UpdateEvidenceMetadataCommand;
import com.naesan.evidence.application.UpdateEvidenceMetadataService;
import com.naesan.evidence.domain.PurchaseEvidence;
import com.naesan.evidence.domain.EvidenceFile;
import com.naesan.evidence.domain.EvidenceSnapshot;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/evidence")
public class EvidenceApiController {
    private final CreateEvidenceDraftService createEvidenceDraftService;
    private final AttachEvidenceFileService attachEvidenceFileService;
    private final UpdateEvidenceMetadataService updateEvidenceMetadataService;
    private final ConfirmEvidenceService confirmEvidenceService;

    public EvidenceApiController(
            CreateEvidenceDraftService createEvidenceDraftService,
            AttachEvidenceFileService attachEvidenceFileService,
            UpdateEvidenceMetadataService updateEvidenceMetadataService,
            ConfirmEvidenceService confirmEvidenceService
    ) {
        this.createEvidenceDraftService = createEvidenceDraftService;
        this.attachEvidenceFileService = attachEvidenceFileService;
        this.updateEvidenceMetadataService = updateEvidenceMetadataService;
        this.confirmEvidenceService = confirmEvidenceService;
    }

    @PostMapping
    public ResponseEntity<EvidenceResponse> createDraft(
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
                .body(EvidenceResponse.from(evidence));
    }

    @PutMapping("/{evidenceId}/metadata")
    public EvidenceResponse updateMetadata(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID evidenceId,
            @Valid @RequestBody UpdateEvidenceMetadataRequest request
    ) {
        PurchaseEvidence evidence = updateEvidenceMetadataService.update(
                new UpdateEvidenceMetadataCommand(
                        account.id(),
                        evidenceId,
                        request.merchantName(),
                        request.productName(),
                        request.serialNumber(),
                        request.purchasedAt(),
                        request.amount(),
                        request.currency()
                )
        );
        return EvidenceResponse.from(evidence);
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

    @PostMapping("/{evidenceId}/confirm")
    public EvidenceConfirmationResponse confirm(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID evidenceId
    ) {
        EvidenceSnapshot snapshot = confirmEvidenceService.confirm(
                account.id(),
                evidenceId
        );
        return EvidenceConfirmationResponse.from(snapshot);
    }
}
