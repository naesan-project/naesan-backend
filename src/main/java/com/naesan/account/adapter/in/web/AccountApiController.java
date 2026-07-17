package com.naesan.account.adapter.in.web;

import java.net.URI;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.naesan.account.application.RegisterAccountService;
import com.naesan.account.domain.Account;

@RestController
@RequestMapping("/api/accounts")
public class AccountApiController {
    private final RegisterAccountService registerAccountService;

    public AccountApiController(RegisterAccountService registerAccountService) {
        this.registerAccountService = registerAccountService;
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
}
