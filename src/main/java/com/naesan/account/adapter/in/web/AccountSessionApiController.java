package com.naesan.account.adapter.in.web;

import java.net.URI;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.account.application.AuthenticateAccountService;
import com.naesan.account.domain.Account;
import com.naesan.security.AuthenticatedAccount;
import com.naesan.security.TokenSession;
import com.naesan.security.TokenSessionException;
import com.naesan.security.TokenSessionManager;

@RestController
@RequestMapping("/api/sessions")
public class AccountSessionApiController {
    private static final URI CURRENT_SESSION_URI = URI.create("/api/sessions/current");

    private final AuthenticateAccountService authenticateAccountService;
    private final TokenSessionManager tokenSessionManager;
    private final RefreshTokenCookieManager refreshTokenCookieManager;

    public AccountSessionApiController(
            AuthenticateAccountService authenticateAccountService,
            TokenSessionManager tokenSessionManager,
            RefreshTokenCookieManager refreshTokenCookieManager
    ) {
        this.authenticateAccountService = authenticateAccountService;
        this.tokenSessionManager = tokenSessionManager;
        this.refreshTokenCookieManager = refreshTokenCookieManager;
    }

    @PostMapping
    public ResponseEntity<TokenSessionResponse> createSession(
            @Valid @RequestBody CreateAccountSessionRequest sessionRequest,
            HttpServletResponse httpResponse
    ) {
        Account account = authenticateAccountService.authenticate(
                sessionRequest.email(),
                sessionRequest.password()
        );
        TokenSession tokenSession = tokenSessionManager.start(account);
        refreshTokenCookieManager.write(httpResponse, tokenSession);

        return ResponseEntity.created(CURRENT_SESSION_URI)
                .body(TokenSessionResponse.from(tokenSession));
    }

    @PostMapping("/refresh")
    public TokenSessionResponse refreshSession(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String rawRefreshToken = refreshTokenCookieManager.read(request)
                .orElseThrow(TokenSessionException::invalidRefreshToken);
        TokenSession tokenSession = tokenSessionManager.refresh(rawRefreshToken);
        refreshTokenCookieManager.write(response, tokenSession);
        return TokenSessionResponse.from(tokenSession);
    }

    @GetMapping("/current")
    public AccountSessionResponse currentSession(
            @AuthenticationPrincipal AuthenticatedAccount authenticatedAccount
    ) {
        return AccountSessionResponse.from(authenticatedAccount);
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> deleteCurrentSession(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        refreshTokenCookieManager.read(request)
                .ifPresent(tokenSessionManager::revoke);
        refreshTokenCookieManager.clear(response);
        return ResponseEntity.noContent().build();
    }
}
