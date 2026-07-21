package com.naesan.share.adapter.in.web;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.security.AuthenticatedAccount;
import com.naesan.share.application.IssuedPublicShare;
import com.naesan.share.application.ManagePublicShareService;

@RestController
@RequestMapping("/api/passports/{passportId}/shares")
public class PublicShareManagementApiController {
    private final ManagePublicShareService managePublicShareService;

    public PublicShareManagementApiController(
            ManagePublicShareService managePublicShareService
    ) {
        this.managePublicShareService = managePublicShareService;
    }

    @PostMapping
    public ResponseEntity<IssuedPublicShareResponse> issue(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId,
            @Valid @RequestBody IssuePublicShareRequest request
    ) {
        IssuedPublicShare issuedShare = managePublicShareService.issue(
                account.id(),
                passportId,
                request.capability()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(IssuedPublicShareResponse.from(issuedShare));
    }

    @PostMapping("/rotation")
    public ResponseEntity<IssuedPublicShareResponse> rotate(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId,
            @Valid @RequestBody RotatePublicShareRequest request
    ) {
        IssuedPublicShare issuedShare = managePublicShareService.rotate(
                account.id(),
                passportId,
                request.capability()
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(IssuedPublicShareResponse.from(issuedShare));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId,
            @PathVariable UUID shareId
    ) {
        managePublicShareService.revoke(account.id(), passportId, shareId);
        return ResponseEntity.noContent().build();
    }
}
