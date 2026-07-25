package com.naesan.passport.adapter.in.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.passport.application.IssuedPassport;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.passport.application.GetPassportDetailsService;
import com.naesan.passport.application.ListPassportsService;
import com.naesan.passport.application.ListOwnershipHistoryService;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/passports")
public class PassportApiController {
    private final IssuePassportService issuePassportService;
    private final ListPassportsService listPassportsService;
    private final GetPassportDetailsService getPassportDetailsService;
    private final ListOwnershipHistoryService listOwnershipHistoryService;

    public PassportApiController(
            IssuePassportService issuePassportService,
            ListPassportsService listPassportsService,
            GetPassportDetailsService getPassportDetailsService,
            ListOwnershipHistoryService listOwnershipHistoryService
    ) {
        this.issuePassportService = issuePassportService;
        this.listPassportsService = listPassportsService;
        this.getPassportDetailsService = getPassportDetailsService;
        this.listOwnershipHistoryService = listOwnershipHistoryService;
    }

    @PostMapping
    public ResponseEntity<PassportResponse> issue(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @Valid @RequestBody IssuePassportRequest request
    ) {
        IssuedPassport issuedPassport = issuePassportService.issue(
                account.id(),
                request.snapshotId()
        );
        URI location = URI.create("/api/passports/" + issuedPassport.passport().id());
        return ResponseEntity.created(location)
                .body(PassportResponse.from(issuedPassport));
    }

    @GetMapping
    public List<PassportResponse> list(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        return listPassportsService.list(account.id())
                .stream()
                .map(PassportResponse::from)
                .toList();
    }

    @GetMapping("/{passportId}")
    public PassportResponse details(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId
    ) {
        return PassportResponse.from(
                getPassportDetailsService.get(account.id(), passportId)
        );
    }

    @GetMapping("/{passportId}/ownership-history")
    public List<OwnershipHistoryResponse> ownershipHistory(
            @AuthenticationPrincipal AuthenticatedAccount account,
            @PathVariable UUID passportId
    ) {
        return listOwnershipHistoryService.list(account.id(), passportId)
                .stream()
                .map(OwnershipHistoryResponse::from)
                .toList();
    }
}
