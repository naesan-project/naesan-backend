package com.naesan.passport.adapter.in.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.passport.application.IssuedPassport;
import com.naesan.passport.application.IssuePassportService;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/passports")
public class PassportApiController {
    private final IssuePassportService issuePassportService;

    public PassportApiController(IssuePassportService issuePassportService) {
        this.issuePassportService = issuePassportService;
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
}
