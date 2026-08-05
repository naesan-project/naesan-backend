package com.naesan.account.adapter.in.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.account.application.DeleteAccountService;
import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;
import com.naesan.security.AuthenticatedAccount;

@RestController
@RequestMapping("/api/accounts")
public class AccountApiController {
    private final RegisterAccountService registerAccountService;
    private final DeleteAccountService deleteAccountService;

    public AccountApiController(
            RegisterAccountService registerAccountService,
            DeleteAccountService deleteAccountService
    ) {
        this.registerAccountService = registerAccountService;
        this.deleteAccountService = deleteAccountService;
    }

    @PostMapping
    public ResponseEntity<RegisterAccountResponse> registerAccount(
            @Valid @RequestBody RegisterAccountRequest request
    ) {
        Account account = registerAccountService.register(request.email(), request.password());
        RegisterAccountResponse response = RegisterAccountResponse.from(account);
        URI location = URI.create("/api/accounts/" + account.id());

        return ResponseEntity.created(location).body(response);
    }

    @DeleteMapping("/current")
    public ResponseEntity<Void> deleteCurrentAccount(
            @AuthenticationPrincipal AuthenticatedAccount account
    ) {
        deleteAccountService.delete(account.id());
        return ResponseEntity.noContent().build();
    }
}
