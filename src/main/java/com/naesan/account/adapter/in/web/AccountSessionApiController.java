package com.naesan.account.adapter.in.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.domain.Account;
import com.naesan.security.AccountSessionManager;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/sessions")
public class AccountSessionApiController {
    private static final URI CURRENT_SESSION_URI = URI.create("/api/sessions/current");

    private final AuthenticateAccountService authenticateAccountService;
    private final AccountSessionManager accountSessionManager;

    public AccountSessionApiController(
            AuthenticateAccountService authenticateAccountService,
            AccountSessionManager accountSessionManager
    ) {
        this.authenticateAccountService = authenticateAccountService;
        this.accountSessionManager = accountSessionManager;
    }

    @PostMapping
    public ResponseEntity<AccountSessionResponse> createSession(
            @Valid @RequestBody CreateAccountSessionRequest sessionRequest,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        Account account = authenticateAccountService.authenticate(
                sessionRequest.email(),
                sessionRequest.password()
        );
        AuthenticatedAccount authenticatedAccount =
                accountSessionManager.startAuthenticatedSession(
                        account,
                        httpRequest,
                        httpResponse
                );

        return ResponseEntity.created(CURRENT_SESSION_URI)
                .body(AccountSessionResponse.from(authenticatedAccount));
    }

    @GetMapping("/current")
    public AccountSessionResponse currentSession(
            @AuthenticationPrincipal AuthenticatedAccount authenticatedAccount
    ) {
        return AccountSessionResponse.from(authenticatedAccount);
    }
}
